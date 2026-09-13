package in.pragati.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.pragati.ai.AiServiceClient;
import in.pragati.config.AppProperties;
import in.pragati.domain.*;
import in.pragati.domain.enums.*;
import in.pragati.repo.*;
import in.pragati.security.AuthUser;

/**
 * End-to-end resume upload tests (deterministic fallback — the AI client is
 * forced offline so the pure-lexicon pipeline is exercised):
 *  - a readable DOCX → PENDING_REVIEW with evidence-backed suggestions;
 *  - a scanned/image-only PDF (valid file, no text) → FAILED, honestly;
 *  - nothing is ever marked DONE before the candidate reviews.
 */
class ResumeUploadTest {

    @TempDir
    java.nio.file.Path tmp;

    private static final AuthUser ME = new AuthUser(100L, "candidate@pragati.gov.in", "Aarav Sharma", "CANDIDATE");

    private ResumeFileRepository resumes;
    private AuditService audit;
    private AiServiceClient ai;
    private CandidateService svc;
    private ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        resumes = mock(ResumeFileRepository.class);
        audit = mock(AuditService.class);
        ai = mock(AiServiceClient.class);
        // AI offline → the deterministic taxonomy fallback must carry the pipeline.
        when(ai.extractSkills(any(String.class)))
                .thenThrow(new IllegalStateException("AI service offline"));
        // Mirror real DB behaviour: findTop returns the most recently saved resume.
        java.util.concurrent.atomic.AtomicReference<ResumeFile> last = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(inv -> {
            last.set(inv.getArgument(0));
            return inv.getArgument(0);
        }).when(resumes).save(any(ResumeFile.class));
        when(resumes.findTopByUserIdOrderByIdDesc(100L))
                .thenAnswer(inv -> Optional.ofNullable(last.get()));

        AppProperties props = new AppProperties();
        props.getUploads().setDir(tmp.resolve("uploads").toString());

        svc = new CandidateService(
                mock(UserRepository.class), mock(CandidateProfileRepository.class),
                mock(CandidateSkillRepository.class), mock(CandidateInterestRepository.class),
                mock(CandidatePreferenceRepository.class), mock(OpportunityRepository.class),
                mock(OpportunitySkillRepository.class), mock(AssignmentRepository.class),
                mock(AllocationRunRepository.class), mock(DatasetSnapshotRepository.class),
                resumes, new SuitabilityService(), new ReadinessService(),
                new ResumeIntelligenceService(), audit, ai, props, json);
    }

    private ResumeFile lastSavedResume() {
        ArgumentCaptor<ResumeFile> cap = ArgumentCaptor.forClass(ResumeFile.class);
        verify(resumes, atLeastOnce()).save(cap.capture());
        return cap.getAllValues().get(cap.getAllValues().size() - 1);
    }

    @Test
    void readableDocxBecomesPendingReviewWithEvidenceBackedSuggestions() throws Exception {
        XWPFDocument doc = new XWPFDocument();
        doc.createParagraph().createRun().setText("Aarav Sharma");
        doc.createParagraph().createRun().setText("Phone: +91 98765 43210");
        doc.createParagraph().createRun().setText("Bengaluru, Karnataka");
        doc.createParagraph().createRun().setText("Final year student, B.Tech in Computer Science");
        doc.createParagraph().createRun().setText("Skills: Python, SQL, Data Analysis");
        doc.createParagraph().createRun().setText("Built a demand-forecasting dashboard for a retail startup.");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.write(bos);
        doc.close();

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                bos.toByteArray());

        svc.uploadResume(ME, file);

        ResumeFile saved = lastSavedResume();
        assertEquals("PENDING_REVIEW", saved.status,
                "a readable resume with suggestions must wait for candidate review — never auto-apply");
        assertNotNull(saved.suggestionsJson, "suggestions must be stored for the review UI");

        var root = json.readTree(saved.suggestionsJson);
        var fields = root.path("fields");
        assertTrue(fields.size() >= 3, "name, phone, state at minimum should be suggested: " + fields);
        for (var f : fields) {
            assertEquals(true, f.hasNonNull("confidence"), "every field needs confidence: " + f);
            assertEquals(true, f.hasNonNull("evidence"), "every field needs evidence: " + f);
            assertFalse(f.path("evidence").asText().isBlank(), "evidence must not be blank");
        }
        var nameField = json.readTree(saved.suggestionsJson).path("fields")
                .toString();
        assertTrue(nameField.contains("Aarav Sharma"), "the extracted name must appear: " + nameField);

        // Deterministic fallback skills: canonical taxonomy only.
        var skillNode = json.readTree(saved.skillsJson);
        List<String> skills = new java.util.ArrayList<>();
        for (var n : skillNode) skills.add(n.asText());
        assertTrue(skills.contains("Python"), "fallback must find 'Python': " + skills);
        assertTrue(skills.contains("SQL"), "fallback must find 'SQL': " + skills);
        assertTrue(skills.contains("Data Analysis"), "fallback must find 'Data Analysis': " + skills);
        for (String s : skills) {
            assertTrue(SkillTaxonomy.CANONICAL.contains(s), "every stored skill must be canonical: " + s);
        }

        verify(audit, atLeastOnce()).log(eq(ME), eq(AuditAction.RESUME_UPLOADED), any(), any(), any(), any());
    }

    @Test
    void scannedPdfWithNoReadableTextFailsHonestly() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "scanned.pdf", "application/pdf", emptyPdfBytes());

        CandidateService.ResumeInfo info = svc.uploadResume(ME, file);

        assertEquals("FAILED", info.status(),
                "a scanned PDF must be reported as unreadable, not half-processed: " + info.status());
        assertNotNull(info.statusMessage());
        assertTrue(info.statusMessage().toLowerCase().contains("read"),
                "the message must explain the problem: " + info.statusMessage());
        verify(audit, atLeastOnce()).log(eq(ME), eq(AuditAction.RESUME_UPLOADED), any(), any(), any(), any());
    }

    @Test
    void unsupportedFileTypeIsRejectedUpfront() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "plain text resume".getBytes(StandardCharsets.UTF_8));
        assertThrows(in.pragati.common.ApiException.class, () -> svc.uploadResume(ME, file));
        verify(resumes, never()).save(any(ResumeFile.class));
    }

    /** A structurally valid PDF with zero pages — i.e. a scanned document with no text layer. */
    private byte[] emptyPdfBytes() {
        StringBuilder sb = new StringBuilder();
        sb.append("%PDF-1.4\n");
        int obj1 = sb.length();
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        int obj2 = sb.length();
        sb.append("2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n");
        int xref = sb.length();
        sb.append("xref\n0 3\n");
        sb.append(String.format("%010d 65535 f \n", 0));
        sb.append(String.format("%010d 00000 n \n", obj1));
        sb.append(String.format("%010d 00000 n \n", obj2));
        sb.append("trailer\n<< /Size 3 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }
}

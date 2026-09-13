package in.pragati.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.pragati.ai.AiServiceClient;
import in.pragati.common.ApiException;
import in.pragati.config.AppProperties;
import in.pragati.domain.*;
import in.pragati.domain.enums.*;
import in.pragati.repo.*;
import in.pragati.security.AuthUser;

/**
 * Resume replacement-safety and confirmation-safety tests (§15 / §39):
 *  - suggestions are bound to the resume they were generated from —
 *    a stale resumeId (user replaced the resume) must be rejected, never applied;
 *  - only values the resume actually suggested may be confirmed;
 *  - a resume that is not (or no longer) awaiting review cannot be re-applied.
 */
class ResumeFlowTest {

    private static final AuthUser ME = new AuthUser(100L, "candidate@pragati.gov.in", "Aarav Sharma", "CANDIDATE");

    private CandidateProfileRepository profiles;
    private ResumeFileRepository resumes;
    private CandidateService svc;

    @BeforeEach
    void setUp() {
        profiles = mock(CandidateProfileRepository.class);
        resumes = mock(ResumeFileRepository.class);
        svc = new CandidateService(
                mock(UserRepository.class), profiles,
                mock(CandidateSkillRepository.class), mock(CandidateInterestRepository.class),
                mock(CandidatePreferenceRepository.class), mock(OpportunityRepository.class),
                mock(OpportunitySkillRepository.class), mock(AssignmentRepository.class),
                mock(AllocationRunRepository.class), mock(DatasetSnapshotRepository.class),
                resumes, new SuitabilityService(), new ReadinessService(),
                new ResumeIntelligenceService(), mock(AuditService.class),
                mock(AiServiceClient.class), new AppProperties(), new ObjectMapper());

        CandidateProfile p = new CandidateProfile();
        p.id = 1L;
        p.userId = 100L;
        p.fullName = "Aarav Sharma";
        p.qualification = Qualification.BACHELORS;
        p.candidateStatus = CandidateStatus.STUDENT;
        p.candidateState = CandidateState.ACTIVE;
        p.state = "Delhi";
        when(profiles.findByUserId(100L)).thenReturn(Optional.of(p));
    }

    private ResumeFile resume(long id, String status, String suggestionsJson) {
        ResumeFile r = new ResumeFile();
        r.id = id;
        r.userId = 100L;
        r.status = status;
        r.suggestionsJson = suggestionsJson;
        return r;
    }

    private static final String SUGGESTED = """
            {"fields":[
              {"field":"fullName","value":"Aarav Sharma","confidence":"High","evidence":"Resume line 1"},
              {"field":"phone","value":"+919876543210","confidence":"High","evidence":"Contact line"}
            ],"skills":["Python","SQL"],"interests":["DATA_ANALYTICS"]}
            """;

    @Test
    void staleResumeIdAfterReplacementIsRejected() {
        // The user uploaded a newer resume (id 2); the frontend still holds
        // suggestions from the replaced resume (id 1). Applying them must fail
        // with a conflict — never apply stale suggestions to the profile.
        when(resumes.findTopByUserIdOrderByIdDesc(100L)).thenReturn(Optional.of(resume(2L, "PENDING_REVIEW", SUGGESTED)));

        CandidateService.ApplyInput in = new CandidateService.ApplyInput(
                1L, Map.of("fullName", "Aarav Sharma"), List.of(), List.of());
        ApiException ex = assertThrows(ApiException.class, () -> svc.applySuggestions(ME, in));
        assertTrue(ex.getMessage().contains("newer resume"), ex.getMessage());
        verify(profiles, never()).save(any(CandidateProfile.class));
        verify(resumes, never()).save(any(ResumeFile.class));
    }

    @Test
    void fieldNotSuggestedByTheResumeCannotBeConfirmed() {
        when(resumes.findTopByUserIdOrderByIdDesc(100L)).thenReturn(Optional.of(resume(2L, "PENDING_REVIEW", SUGGESTED)));

        // "qualification" was never in the suggestions → must be rejected,
        // so a candidate can never "confirm" a fabricated value.
        CandidateService.ApplyInput in = new CandidateService.ApplyInput(
                2L, Map.of("qualification", "DOCTORAL"), List.of(), List.of());
        assertThrows(ApiException.class, () -> svc.applySuggestions(ME, in));
    }

    @Test
    void skillNotSuggestedByTheResumeCannotBeConfirmed() {
        when(resumes.findTopByUserIdOrderByIdDesc(100L)).thenReturn(Optional.of(resume(2L, "PENDING_REVIEW", SUGGESTED)));

        CandidateService.ApplyInput in = new CandidateService.ApplyInput(
                2L, Map.of(), List.of("Quantum Computing"), List.of());
        ApiException ex = assertThrows(ApiException.class, () -> svc.applySuggestions(ME, in));
        assertTrue(ex.getMessage().contains("not suggested"), ex.getMessage());
    }

    @Test
    void alreadyReviewedResumeCannotBeAppliedAgain() {
        when(resumes.findTopByUserIdOrderByIdDesc(100L)).thenReturn(Optional.of(resume(2L, "REVIEWED", SUGGESTED)));

        CandidateService.ApplyInput in = new CandidateService.ApplyInput(
                2L, Map.of("fullName", "Aarav Sharma"), List.of(), List.of());
        ApiException ex = assertThrows(ApiException.class, () -> svc.applySuggestions(ME, in));
        assertTrue(ex.getMessage().contains("reviewed"), ex.getMessage());
    }

    @Test
    void unknownProfileFieldIsRejected() {
        when(resumes.findTopByUserIdOrderByIdDesc(100L)).thenReturn(Optional.of(resume(2L, "PENDING_REVIEW", SUGGESTED)));

        CandidateService.ApplyInput in = new CandidateService.ApplyInput(
                2L, Map.of("salary", "500000"), List.of(), List.of());
        assertThrows(ApiException.class, () -> svc.applySuggestions(ME, in));
    }
}

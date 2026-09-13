package in.pragati.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Tests the deterministic resume extraction contract:
 *  - every suggested field carries confidence AND evidence;
 *  - nothing is ever fabricated (no field without visible evidence);
 *  - skills are normalized to the canonical taxonomy.
 */
class ResumeIntelligenceServiceTest {

    private final ResumeIntelligenceService svc = new ResumeIntelligenceService();

    private Optional<ResumeIntelligenceService.FieldSuggestion> field(
            ResumeIntelligenceService.ResumeSuggestions s, String name) {
        return s.fields().stream().filter(f -> f.field().equals(name)).findFirst();
    }

    @Test
    void extractsFieldsWithConfidenceAndEvidenceFromRichText() {
        String text = """
                Aarav Sharma
                Phone: +91 98765 43210
                Bengaluru, Karnataka
                B.Tech in Computer Science, Class of 2026
                Final year student

                Skills: Python, SQL, Data Analysis, Machine Learning
                Built a demand-forecasting dashboard for a retail startup.
                Interested in data analysis and analytics projects.
                """;
        ResumeIntelligenceService.ResumeSuggestions s = svc.extract(text);

        Optional<ResumeIntelligenceService.FieldSuggestion> name = field(s, "fullName");
        assertTrue(name.isPresent(), "name must be suggested");
        assertEquals("Aarav Sharma", name.get().value());
        assertNotNull(name.get().confidence());
        assertFalse(name.get().evidence().isBlank(), "every field needs visible evidence");

        Optional<ResumeIntelligenceService.FieldSuggestion> phone = field(s, "phone");
        assertTrue(phone.isPresent(), "phone must be suggested");
        assertTrue(phone.get().value().contains("9876543210"), "phone digits must match: " + phone.get().value());

        Optional<ResumeIntelligenceService.FieldSuggestion> state = field(s, "state");
        assertTrue(state.isPresent());
        assertEquals("Karnataka", state.get().value());

        Optional<ResumeIntelligenceService.FieldSuggestion> district = field(s, "district");
        assertTrue(district.isPresent());
        assertEquals("Bengaluru", district.get().value());

        Optional<ResumeIntelligenceService.FieldSuggestion> qual = field(s, "qualification");
        assertTrue(qual.isPresent(), "qualification must be suggested");
        assertTrue(qual.get().value().equals("BACHELORS"), "B.Tech → BACHELORS, got " + qual.get().value());

        Optional<ResumeIntelligenceService.FieldSuggestion> status = field(s, "candidateStatus");
        assertTrue(status.isPresent());
        assertTrue(status.get().value().equals("STUDENT"), "final year student → STUDENT, got " + status.get().value());

        // Skill extraction is deliberately NOT part of field extraction — the
        // caller fills skills through the validated (AI/lexicon) pipeline, so
        // extract() must never fabricate skill suggestions on its own.
        assertTrue(s.skills().isEmpty(),
                "extract() must not invent skills — the caller's validated pipeline supplies them: " + s.skills());

        // Interests from the interest sentence.
        assertFalse(s.interests().isEmpty(), "interest keywords should map to at least one sector");
    }

    @Test
    void neverFabricatesFieldsAbsentFromTheText() {
        String text = "Aarav Sharma\nBengaluru, Karnataka\nStudent interested in analytics.";
        ResumeIntelligenceService.ResumeSuggestions s = svc.extract(text);

        assertFalse(field(s, "phone").isPresent(), "no phone in text → no phone suggestion (never invented)");
        assertFalse(field(s, "experienceYears").isPresent(), "no experience in text → no experience suggestion");
        assertFalse(field(s, "qualification").isPresent(), "no qualification in text → no qualification suggestion");
        assertTrue(s.skills().isEmpty(), "no skill keywords in text → no skills suggested");
    }

    @Test
    void districtIsOnlySuggestedWhenItIsKnownForTheState() {
        // "Noida" is not in the known-district table → state may be suggested,
        // district must not be invented.
        String text = "Riya Verma\nNoida, Delhi\nGraduate";
        ResumeIntelligenceService.ResumeSuggestions s = svc.extract(text);

        assertTrue(field(s, "state").map(f -> f.value().equals("Delhi")).orElse(false),
                "Delhi should be recognized as the state");
        assertFalse(field(s, "district").isPresent(),
                "unknown district 'Noida' must not be suggested as a district");
    }

    @Test
    void emptyOrBlankTextProducesNoSuggestionsAtAll() {
        ResumeIntelligenceService.ResumeSuggestions s = svc.extract("");
        assertTrue(s.fields().isEmpty());
        assertTrue(s.skills().isEmpty());
        assertTrue(s.interests().isEmpty());

        ResumeIntelligenceService.ResumeSuggestions s2 = svc.extract(null);
        assertTrue(s2.fields().isEmpty());
    }

    @Test
    void shortResumesNeverCrashExtraction() {
        // A 3-line resume (limit math in state/district extraction) must not
        // blow up — this used to index past the end of the line list.
        assertDoesNotThrow(() -> svc.extract("Riya Verma\nNoida, Delhi\nGraduate"));
        assertDoesNotThrow(() -> svc.extract("Single line resume"));
    }
}

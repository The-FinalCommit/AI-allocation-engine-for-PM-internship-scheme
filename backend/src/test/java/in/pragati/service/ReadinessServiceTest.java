package in.pragati.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import in.pragati.domain.CandidateProfile;
import in.pragati.domain.enums.*;

/**
 * PENDING_REVIEW semantics (§15): an uploaded-but-unreviewed resume is NOT
 * complete. Only a resume the candidate has reviewed and confirmed counts
 * toward readiness.
 */
class ReadinessServiceTest {

    private final ReadinessService svc = new ReadinessService();

    private CandidateProfile completeProfile() {
        CandidateProfile p = new CandidateProfile();
        p.fullName = "Aarav Sharma";
        p.phone = "9876543210";
        p.dob = LocalDate.of(2000, 1, 1);
        p.state = "Delhi";
        p.district = "New Delhi";
        p.locationType = LocationType.URBAN;
        p.qualification = Qualification.BACHELORS;
        p.candidateStatus = CandidateStatus.STUDENT;
        p.bio = "Final-year student interested in data and software internships.";
        return p;
    }

    @Test
    void completeProfileWithReviewedResumeScoresFullReadiness() {
        ReadinessService.Readiness r = svc.compute(completeProfile(), 4, 2, 3, "REVIEWED");
        assertEquals(100, r.overall(),
                "a fully complete profile with a reviewed resume must reach 100: " + r.overall());
    }

    @Test
    void pendingReviewResumeDoesNotCountAsComplete() {
        ReadinessService.Readiness pending = svc.compute(completeProfile(), 4, 2, 3, "PENDING_REVIEW");
        ReadinessService.Readiness reviewed = svc.compute(completeProfile(), 4, 2, 3, "REVIEWED");

        int resumeItemMax = pending.items().stream()
                .filter(i -> i.key().equalsIgnoreCase("resume")).mapToInt(ReadinessService.Item::maxScore).findFirst().orElse(0);
        assertTrue(resumeItemMax > 0, "a resume item must exist in readiness");

        int pendingResumeEarned = pending.items().stream()
                .filter(i -> i.key().equalsIgnoreCase("resume")).mapToInt(ReadinessService.Item::earned).findFirst().orElse(-1);
        int reviewedResumeEarned = reviewed.items().stream()
                .filter(i -> i.key().equalsIgnoreCase("resume")).mapToInt(ReadinessService.Item::earned).findFirst().orElse(-1);

        assertEquals(0, pendingResumeEarned, "PENDING_REVIEW must earn 0 on the resume item");
        assertEquals(resumeItemMax, reviewedResumeEarned, "REVIEWED must earn full resume marks");
        assertTrue(reviewed.overall() > pending.overall(),
                "reviewed (" + reviewed.overall() + ") must beat pending (" + pending.overall() + ")");
    }

    @Test
    void failedResumeIsReportedNotCountedAndExplained() {
        ReadinessService.Readiness r = svc.compute(completeProfile(), 4, 2, 3, "FAILED");
        ReadinessService.Item resume = r.items().stream()
                .filter(i -> i.key().equalsIgnoreCase("resume")).findFirst().orElseThrow();
        assertEquals(0, resume.earned(), "a failed resume earns nothing");
        assertTrue(resume.detail().toLowerCase().contains("resume"),
                "the failed state must explain the next action: " + resume.detail());
    }

    @Test
    void noResumeAtAllIsHonestAboutTheMissingPart() {
        ReadinessService.Readiness r = svc.compute(completeProfile(), 4, 2, 3, null);
        ReadinessService.Item resume = r.items().stream()
                .filter(i -> i.key().equalsIgnoreCase("resume")).findFirst().orElseThrow();
        assertEquals(0, resume.earned());
        assertTrue(r.overall() < 100, "missing resume must keep readiness below 100: " + r.overall());
    }
}

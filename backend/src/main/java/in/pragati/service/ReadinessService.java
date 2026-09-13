package in.pragati.service;

import java.util.List;

import org.springframework.stereotype.Service;

import in.pragati.domain.CandidateProfile;

/**
 * Allocation readiness: a real, calculated completeness score for a
 * candidate profile. Readiness never guarantees allocation.
 */
@Service
public class ReadinessService {

    public record Item(String key, String label, int maxScore, int earned, String status, String detail) { }
    public record Readiness(int overall, List<Item> items) { }

    public Readiness compute(CandidateProfile p, int skillCount, int interestCount,
                             int preferenceCount, String resumeState) {
        // Only a REVIEWED resume counts as complete. An unreviewed resume
        // (PENDING_REVIEW) still needs candidate action, so it does not.
        boolean resumeDone = "REVIEWED".equals(resumeState);
        String resumeDetail = switch (resumeState == null ? "" : resumeState) {
            case "REVIEWED" -> "Resume reviewed and confirmed.";
            case "PENDING_REVIEW" -> "Resume uploaded — it is waiting for you to review the extracted suggestions.";
            case "FAILED" -> "The last resume could not be read. Upload a text-based PDF or a Word document.";
            default -> "Upload a resume (PDF or Word) to let PRAGATI find more skills.";
        };
        List<Item> items = new java.util.ArrayList<>();

        int basic = 0;
        if (p.fullName != null && !p.fullName.isBlank()) basic++;
        if (p.phone != null && !p.phone.isBlank()) basic++;
        if (p.dob != null) basic++;
        if (p.state != null && !p.state.isBlank()) basic++;
        if (p.district != null && !p.district.isBlank()) basic++;
        if (p.locationType != null) basic++;
        int basicEarned = (int) (basic * 25.0 / 6);
        items.add(new Item("basic", "Basic information", 25, basicEarned,
                basic == 6 ? "COMPLETED" : "NEEDS_ATTENTION",
                "Name, phone, date of birth, state, district and location type."));

        int edu = 0;
        if (p.qualification != null) edu++;
        if (p.candidateStatus != null) edu++;
        if (p.bio != null && p.bio.length() >= 20) edu++;
        int eduEarned = (int) (edu * 15.0 / 3);
        items.add(new Item("education", "Education & status", 15, eduEarned,
                edu == 3 ? "COMPLETED" : "NEEDS_ATTENTION",
                "Qualification, current status and a short introduction."));

        int skillsEarned = skillCount >= 3 ? 20 : skillCount * 6;
        items.add(new Item("skills", "Skills", 20, skillsEarned,
                skillCount >= 3 ? "COMPLETED" : "NEEDS_ATTENTION",
                "Add at least 3 skills (manually or from your resume)."));

        int intEarned = interestCount >= 1 ? 10 : 0;
        items.add(new Item("interests", "Interests", 10, intEarned,
                interestCount >= 1 ? "COMPLETED" : "NEEDS_ATTENTION",
                "Select the sectors you are interested in."));

        int prefEarned = preferenceCount >= 1 ? 15 : 0;
        items.add(new Item("preferences", "Preferences", 15, prefEarned,
                preferenceCount >= 1 ? "COMPLETED" : "NEEDS_ATTENTION",
                "Rank at least one opportunity you would like."));

        int resumeEarned = resumeDone ? 15 : 0;
        items.add(new Item("resume", "Resume", 15, resumeEarned,
                resumeDone ? "COMPLETED" : "NEEDS_ATTENTION",
                resumeDetail));

        int overall = items.stream().mapToInt(Item::earned).sum();
        return new Readiness(Math.min(100, overall), items);
    }
}

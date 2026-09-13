package in.pragati.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import in.pragati.common.Weights;
import in.pragati.domain.AllocationRun;
import in.pragati.domain.CandidateProfile;
import in.pragati.domain.Opportunity;
import in.pragati.domain.enums.OppStatus;
import in.pragati.repo.AllocationRunRepository;
import in.pragati.repo.CandidateInterestRepository;
import in.pragati.repo.CandidatePreferenceRepository;
import in.pragati.repo.CandidateProfileRepository;
import in.pragati.repo.CandidateSkillRepository;
import in.pragati.repo.OpportunityRepository;
import in.pragati.repo.OpportunitySkillRepository;
import in.pragati.repo.ResumeFileRepository;

/**
 * Dataset health checks. Every issue states what is wrong, why it matters
 * and how to fix it. Statuses are computed live — nothing is hard-wired green.
 */
@Service
public class DataQualityService {

    public record Check(String area, String status, String what, String why, String fix, int affected) { }

    private final CandidateProfileRepository profiles;
    private final CandidateSkillRepository skills;
    private final CandidateInterestRepository interests;
    private final CandidatePreferenceRepository preferences;
    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository oppSkills;
    private final AllocationRunRepository runs;
    private final ResumeFileRepository resumes;

    public DataQualityService(CandidateProfileRepository profiles, CandidateSkillRepository skills,
                              CandidateInterestRepository interests,
                              CandidatePreferenceRepository preferences,
                              OpportunityRepository opportunities, OpportunitySkillRepository oppSkills,
                              AllocationRunRepository runs, ResumeFileRepository resumes) {
        this.profiles = profiles; this.skills = skills; this.interests = interests;
        this.preferences = preferences; this.opportunities = opportunities;
        this.oppSkills = oppSkills; this.runs = runs; this.resumes = resumes;
    }

    public List<Check> checks() {
        List<Check> out = new ArrayList<>();
        List<CandidateProfile> all = profiles.findAll();
        int total = all.size();

        long missingQual = all.stream().filter(p -> p.qualification == null).count();
        long missingLocation = all.stream().filter(p -> p.state == null || p.district == null).count();
        long missingPhone = all.stream().filter(p -> p.phone == null || p.phone.isBlank()).count();
        long missingDob = all.stream().filter(p -> p.dob == null).count();

        List<Long> ids = all.stream().map(p -> p.id).toList();
        java.util.Set<Long> withSkills = skills.findByCandidateIdIn(ids).stream().map(s -> s.candidateId).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> withPrefs = preferences.findByCandidateIdIn(ids).stream().map(s -> s.candidateId).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> withInterests = interests.findByCandidateIdIn(ids).stream().map(s -> s.candidateId).collect(java.util.stream.Collectors.toSet());
        int noSkills = (int) ids.stream().filter(id -> !withSkills.contains(id)).count();
        int noPrefs = (int) ids.stream().filter(id -> !withPrefs.contains(id)).count();
        int noInterests = (int) ids.stream().filter(id -> !withInterests.contains(id)).count();

        out.add(check("Profile completeness", missingQual + missingLocation > 0 ? "ATTENTION" : "READY",
                "Candidate profiles with missing key details",
                "Incomplete profiles reduce the accuracy of eligibility and suitability.",
                "Ask candidates to complete their profile sections.",
                (int) (missingQual + missingLocation)));
        out.add(check("Qualification", missingQual > 0 ? "BLOCKED" : "READY",
                "Candidates without a declared qualification",
                "Qualification is a hard eligibility requirement; these candidates cannot be evaluated.",
                "Collect qualification details before the next allocation run.",
                (int) missingQual));
        out.add(check("Skills", noSkills > 0 ? "ATTENTION" : "READY",
                "Candidates with no validated skills",
                "Skill fit is the most heavily weighted suitability factor.",
                "Candidates can add skills manually or upload a resume.",
                noSkills));
        out.add(contact("Location", missingLocation > 0 ? "ATTENTION" : "READY",
                "Candidates missing state or district",
                "Location drives the geography view and the location suitability factor.",
                "Ask candidates to confirm their state and district.",
                (int) missingLocation));
        out.add(check("Preferences", noPrefs > 0 ? "ATTENTION" : "READY",
                "Candidates with no opportunity preferences",
                "Preference satisfaction is a reported outcome; candidates without preferences score low on that factor.",
                "Encourage candidates to rank at least one opportunity.",
                noPrefs));
        out.add(check("Interests", noInterests > 0 ? "ATTENTION" : "READY",
                "Candidates with no declared interests",
                "Interests improve the match between candidates and opportunity sectors.",
                "Candidates can add interests in their profile.",
                noInterests));

        List<Opportunity> active = opportunities.findAllByStatus(OppStatus.ACTIVE);
        long zeroCapacity = active.stream().filter(o -> o.capacity < 1).count();
        out.add(check("Opportunity capacity", zeroCapacity > 0 ? "BLOCKED" : "READY",
                "Active opportunities with zero or missing capacity",
                "Opportunities without seats cannot receive allocations.",
                "Providers should set a seat capacity for every active opportunity.",
                (int) zeroCapacity));

        // Opportunities with no mandatory skill at all cannot define eligibility.
        int noMandatory = 0;
        if (!active.isEmpty()) {
            java.util.Set<Long> withMandatory = oppSkills.findByOpportunityIdIn(active.stream().map(o -> o.id).toList())
                    .stream().filter(s -> s.mandatory).map(s -> s.opportunityId)
                    .collect(java.util.stream.Collectors.toSet());
            noMandatory = (int) active.stream().filter(o -> !withMandatory.contains(o.id)).count();
        }
        out.add(check("Opportunity requirements", noMandatory > 0 ? "BLOCKED" : "READY",
                "Active opportunities without any mandatory skill",
                "Mandatory skills define hard eligibility rules; without one, every active candidate is eligible by default.",
                "Add at least one required skill to each opportunity.",
                noMandatory));

        // Live policy check: the latest completed run's weights must sum to 100.
        long badWeights = 0;
        String policyDetail = "PRAGATI default configurable weights";
        AllocationRun latest = runs.findTopByOrderByIdDesc().orElse(null);
        if (latest != null && latest.weightsJson != null && !latest.weightsJson.isBlank()) {
            policyDetail = "Policy of Allocation Run #" + latest.number;
            try {
                Weights w = Weights.parse(latest.weightsJson, new com.fasterxml.jackson.databind.ObjectMapper());
                if (Math.abs(w.sum() - 100) > 0.001) badWeights = 1;
            } catch (Exception e) {
                badWeights = 1;
            }
        }
        out.add(check("Policy configuration", badWeights > 0 ? "ATTENTION" : "READY",
                "Active policy weights (" + policyDetail + ")",
                "Weights must total exactly 100% for a well-defined objective.",
                "Re-check the policy weights in the Policy Lab.",
                (int) badWeights));
        return out;
    }

    private Check check(String area, String status, String what, String why, String fix, int affected) {
        return new Check(area, status, what, why, fix, affected);
    }

    private Check contact(String area, String status, String what, String why, String fix, int affected) {
        return new Check(area, status, what, why, fix, affected);
    }
}

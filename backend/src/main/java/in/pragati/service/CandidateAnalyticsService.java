package in.pragati.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import in.pragati.ai.AiServiceClient;
import in.pragati.common.ApiException;
import in.pragati.common.Labels;
import in.pragati.common.Weights;
import in.pragati.domain.AllocationRun;
import in.pragati.domain.CandidateProfile;
import in.pragati.domain.Opportunity;
import in.pragati.domain.OpportunitySkill;
import in.pragati.domain.enums.RunStatus;
import in.pragati.domain.enums.Sector;
import in.pragati.repo.AllocationRunRepository;
import in.pragati.repo.CandidateProfileRepository;
import in.pragati.repo.CandidateSkillRepository;
import in.pragati.repo.CandidateInterestRepository;
import in.pragati.repo.CandidatePreferenceRepository;
import in.pragati.repo.OpportunityRepository;
import in.pragati.repo.OpportunitySkillRepository;
import in.pragati.security.AuthUser;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Candidate decision support: Fit Analytics, Top-5 recommendations, skill gaps
 * and the grounded skill-gap assistant.
 *
 * Every number here comes from the SAME SuitabilityService that powers the
 * explorer, the allocation engine and the admin pages — there is no second
 * scoring formula. Eligibility is likewise the single canonical check.
 * Recommendations are individual-fit decision support, never allocation.
 */
@Service
public class CandidateAnalyticsService {

    public record OppInfo(Long id, String title, String sector, String state, String city,
                          int capacity, int durationMonths, String minQualification,
                          boolean active, List<String> mandatorySkills, List<String> niceSkills) { }

    public record Factor(String key, String label, double fit, double weight,
                         double contribution, double lostPoints, String explanation) { }

    public record FitAnalysis(OppInfo opportunity, boolean eligible, List<String> eligibilityReasons,
                              double overallScore, double possibleScore, double lostPoints,
                              String recommendationLabel, String weightsSource,
                              List<Factor> factors, List<String> strongestMatches,
                              List<String> topGaps, Integer preferenceRank) { }

    public record Recommendation(Long rank, OppInfo opportunity, boolean eligible, double overallScore,
                                 List<String> strongestMatches, String topGap, Integer preferenceRank,
                                 String reason) { }

    public record Recommendations(String weightsSource, String note, List<Recommendation> items) { }

    public record SkillGap(OppInfo opportunity, boolean eligible, List<String> coveredSkills,
                           List<String> mandatoryGaps, List<String> preferredGaps,
                           double readinessPercent, String assistantSummary) { }

    public record Guidance(String answer, String engine, String grounding, boolean aiAssisted) { }

    private final CandidateProfileRepository profiles;
    private final CandidateSkillRepository skills;
    private final CandidateInterestRepository interests;
    private final CandidatePreferenceRepository preferences;
    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository oppSkills;
    private final AllocationRunRepository runs;
    private final SuitabilityService suitability;
    private final AiServiceClient ai;
    private final ObjectMapper json;

    public CandidateAnalyticsService(CandidateProfileRepository profiles,
                                     CandidateSkillRepository skills,
                                     CandidateInterestRepository interests,
                                     CandidatePreferenceRepository preferences,
                                     OpportunityRepository opportunities,
                                     OpportunitySkillRepository oppSkills,
                                     AllocationRunRepository runs,
                                     SuitabilityService suitability, AiServiceClient ai, ObjectMapper json) {
        this.profiles = profiles; this.skills = skills; this.interests = interests;
        this.preferences = preferences; this.opportunities = opportunities;
        this.oppSkills = oppSkills; this.runs = runs; this.suitability = suitability;
        this.ai = ai; this.json = json;
    }

    // ------------------------------------------------------------------
    // Shared context
    // ------------------------------------------------------------------

    private record Ctx(CandidateProfile profile, Set<String> skillSet, Set<Sector> interestSet,
                       Map<Long, Integer> prefRanks) { }

    private Ctx context(AuthUser me) {
        CandidateProfile p = profiles.findByUserId(me.id())
                .orElseThrow(() -> ApiException.notFound("Your profile has not been initialized yet."));
        Set<String> skillSet = new TreeSet<>(skills.findByCandidateId(p.id).stream()
                .filter(s -> s.validated).map(s -> s.canonical).toList());
        Set<Sector> interestSet = new HashSet<>(interests.findByCandidateId(p.id).stream()
                .map(i -> i.sector).toList());
        Map<Long, Integer> prefRanks = new HashMap<>();
        for (var cp : preferences.findByCandidateId(p.id)) prefRanks.put(cp.opportunityId, cp.rank);
        return new Ctx(p, skillSet, interestSet, prefRanks);
    }

    private record Policy(Weights weights, String source) { }

    /** Weights from the active policy: the latest completed run's policy when
     *  one exists, otherwise the labelled PRAGATI default configurable weights. */
    private Policy activePolicy() {
        return runs.findTopByOrderByIdDesc()
                .filter(r -> r.status == RunStatus.COMPLETED)
                .map(r -> {
                    try {
                        return new Policy(Weights.parse(r.weightsJson, json),
                                "Policy of Allocation Run #" + r.number);
                    } catch (Exception e) {
                        return new Policy(Weights.defaultBalanced(), "PRAGATI default configurable weights");
                    }
                })
                .orElseGet(() -> new Policy(Weights.defaultBalanced(), "PRAGATI default configurable weights"));
    }

    private Opportunity requireOpp(Long id) {
        return opportunities.findById(id)
                .orElseThrow(() -> ApiException.notFound("Opportunity not found."));
    }

    private OppInfo oppInfo(Opportunity o, List<OpportunitySkill> reqs) {
        return new OppInfo(o.id, o.title, Labels.label(o.sector), o.state, o.city,
                o.capacity, o.durationMonths, Labels.label(o.minQualification),
                o.status == in.pragati.domain.enums.OppStatus.ACTIVE,
                reqs.stream().filter(s -> s.mandatory).map(s -> s.skill).sorted().toList(),
                reqs.stream().filter(s -> !s.mandatory).map(s -> s.skill).sorted().toList());
    }

    // ------------------------------------------------------------------
    // Fit Analytics
    // ------------------------------------------------------------------

    public FitAnalysis fit(AuthUser me, Long opportunityId) {
        Ctx ctx = context(me);
        Opportunity o = requireOpp(opportunityId);
        List<OpportunitySkill> reqs = oppSkills.findByOpportunityId(o.id);
        Policy pol = activePolicy();
        Weights w = pol.weights();

        var elig = suitability.check(ctx.profile(), ctx.skillSet(), o, reqs);
        var score = suitability.score(ctx.profile(), ctx.skillSet(), ctx.interestSet(),
                ctx.prefRanks().get(o.id), o, reqs, w);

        double[] fits = { score.skills(), score.qualification(), score.interest(),
                score.location(), score.preference(), score.learning(), score.experience() };
        double[] weights = { w.skills(), w.qualification(), w.interest(),
                w.location(), w.preference(), w.learning(), w.experience() };
        String[] keys = { "skills", "qualification", "interest", "location", "preference", "learning", "experience" };
        String[] labels = { "Skills match", "Qualification", "Interest fit", "Location match",
                "Preference alignment", "Learning potential", "Experience" };

        List<Factor> factors = new ArrayList<>();
        double overall = 0;
        for (int i = 0; i < fits.length; i++) {
            double contribution = r2(fits[i] * weights[i] / 100.0);
            double lost = r2(weights[i] - contribution);
            overall += contribution;
            factors.add(new Factor(keys[i], labels[i], fits[i], weights[i], contribution, lost,
                    explainFactor(keys[i], ctx, o, reqs, ctx.prefRanks().get(o.id))));
        }
        overall = r1(overall);
        double remaining = r2(100 - overall);

        Set<String> have = ctx.skillSet();
        List<String> coveredMandatory = reqs.stream().filter(s -> s.mandatory && have.contains(s.skill)).map(s -> s.skill).distinct().sorted().toList();
        List<String> coveredNice = reqs.stream().filter(s -> !s.mandatory && have.contains(s.skill)).map(s -> s.skill).distinct().sorted().toList();
        List<String> strongest = new ArrayList<>();
        strongest.addAll(coveredMandatory);
        strongest.addAll(coveredNice);
        List<String> gaps = reqs.stream().filter(s -> !s.mandatory && !have.contains(s.skill)).map(s -> s.skill).distinct().sorted().toList();

        return new FitAnalysis(oppInfo(o, reqs), elig.eligible(), elig.reasons(), overall, 100,
                remaining, labelFor(overall), pol.source(), factors,
                strongest.stream().limit(4).toList(), gaps.stream().limit(3).toList(),
                ctx.prefRanks().get(o.id));
    }

    private String explainFactor(String key, Ctx ctx, Opportunity o, List<OpportunitySkill> reqs, Integer rank) {
        CandidateProfile p = ctx.profile();
        Set<String> have = ctx.skillSet();
        return switch (key) {
            case "skills" -> {
                long mand = reqs.stream().filter(s -> s.mandatory).count();
                long mandHave = reqs.stream().filter(s -> s.mandatory && have.contains(s.skill)).count();
                long nice = reqs.stream().filter(s -> !s.mandatory).count();
                long niceHave = reqs.stream().filter(s -> !s.mandatory && have.contains(s.skill)).count();
                yield "You cover " + mandHave + " of " + mand + " required and " + niceHave + " of "
                        + nice + " preferred listed skills.";
            }
            case "qualification" -> p.qualification == null
                    ? "No qualification is set on your profile yet."
                    : "You hold " + Labels.label(p.qualification) + "; this internship needs "
                          + Labels.label(o.minQualification) + " or higher.";
            case "interest" -> ctx.interestSet().contains(o.sector)
                    ? "Your interests include " + Labels.label(o.sector) + "."
                    : (ctx.interestSet().isEmpty()
                          ? "You have not selected any sector interests yet."
                          : "Your listed interests do not include " + Labels.label(o.sector) + ".");
            case "location" -> {
                if (p.state == null || p.state.isBlank()) yield "No state is set on your profile yet.";
                if (p.state.equalsIgnoreCase(o.state)) yield "You are in " + p.state + " — the same state as the internship.";
                yield "You are in " + p.state + "; the internship is based in " + o.state + ".";
            }
            case "preference" -> rank == null
                    ? "You have not listed this opportunity in your preferences yet."
                    : "This is your #" + rank + " listed preference.";
            case "learning" -> "Based on your qualification"
                    + (p.experienceYears != null && p.experienceYears > 0 ? " and experience" : "")
                    + " and how many validated skills you carry.";
            case "experience" -> p.experienceYears == null || p.experienceYears == 0
                    ? "No professional experience is recorded yet."
                    : "Based on " + fmtYears(p.experienceYears) + " of experience"
                          + (p.candidateStatus == in.pragati.domain.enums.CandidateStatus.WORKING ? " and your working status" : "") + ".";
            default -> "";
        };
    }

    private static String fmtYears(double y) {
        return y == Math.rint(y) ? String.valueOf((long) y) : String.valueOf(y);
    }

    static String labelFor(double score) {
        if (score >= 85) return "Excellent fit";
        if (score >= 70) return "Strong fit";
        if (score >= 50) return "Moderate fit";
        if (score >= 30) return "Partial fit";
        return "Low fit";
    }

    // ------------------------------------------------------------------
    // Top-5 recommendations (decision support — never allocation)
    // ------------------------------------------------------------------

    public Recommendations recommendations(AuthUser me) {
        Ctx ctx = context(me);
        Policy pol = activePolicy();
        List<Opportunity> active = opportunities.findAllByStatus(in.pragati.domain.enums.OppStatus.ACTIVE);
        List<Long> ids = active.stream().map(o -> o.id).toList();
        Map<Long, List<OpportunitySkill>> reqMap = new HashMap<>();
        for (OpportunitySkill s : oppSkills.findByOpportunityIdIn(ids)) {
            reqMap.computeIfAbsent(s.opportunityId, k -> new ArrayList<>()).add(s);
        }
        List<Recommendation> items = new ArrayList<>();
        for (Opportunity o : active) {
            List<OpportunitySkill> reqs = reqMap.getOrDefault(o.id, List.of());
            var elig = suitability.check(ctx.profile(), ctx.skillSet(), o, reqs);
            if (!elig.eligible()) continue; // only active AND eligible opportunities
            var score = suitability.score(ctx.profile(), ctx.skillSet(), ctx.interestSet(),
                    ctx.prefRanks().get(o.id), o, reqs, pol.weights());
            Set<String> have = ctx.skillSet();
            List<String> strongest = reqs.stream().filter(s -> have.contains(s.skill))
                    .map(s -> s.skill).distinct().sorted().limit(3).toList();
            String gap = reqs.stream().filter(s -> !s.mandatory && !have.contains(s.skill))
                    .map(s -> s.skill).sorted().findFirst().orElse(null);
            if (gap == null) {
                gap = reqs.stream().filter(s -> s.mandatory && !have.contains(s.skill))
                        .map(s -> s.skill).sorted().findFirst().orElse(null);
            }
            items.add(new Recommendation(null, oppInfo(o, reqs), true, r1(score.overall()),
                    strongest, gap, ctx.prefRanks().get(o.id), reasonFor(o, ctx, score, gap)));
        }
        items.sort(Comparator.comparingDouble((Recommendation x) -> x.overallScore()).reversed()
                .thenComparing(x -> x.preferenceRank() == null ? 99 : x.preferenceRank())
                .thenComparing(x -> x.opportunity().id()));
        List<Recommendation> top = new ArrayList<>();
        for (int i = 0; i < items.size() && i < 5; i++) {
            var it = items.get(i);
            top.add(new Recommendation(i + 1L, it.opportunity(), it.eligible(), it.overallScore(),
                    it.strongestMatches(), it.topGap(), it.preferenceRank(), it.reason()));
        }
        return new Recommendations(pol.source(),
                "Recommendation — not a final allocation. Recommendations show your individual fit; "
                        + "the final allocation considers the entire candidate pool and available capacity.",
                top);
    }

    private String reasonFor(Opportunity o, Ctx ctx, SuitabilityService.PairScore score, String gap) {
        CandidateProfile p = ctx.profile();
        StringBuilder sb = new StringBuilder();
        if (ctx.interestSet().contains(o.sector)) sb.append("Matches your ").append(Labels.label(o.sector)).append(" interest.");
        if (p.state != null && p.state.equalsIgnoreCase(o.state)) sb.append(" Based in your state (").append(o.state).append(").");
        if (ctx.prefRanks().containsKey(o.id)) sb.append(" You listed it as preference #").append(ctx.prefRanks().get(o.id)).append(".");
        if (sb.isEmpty()) sb.append("Strong overall profile match.");
        if (gap != null) sb.append(" Main gap: ").append(gap).append(".");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Skill gap
    // ------------------------------------------------------------------

    public SkillGap skillGap(AuthUser me, Long opportunityId) {
        Ctx ctx = context(me);
        Opportunity o = requireOpp(opportunityId);
        List<OpportunitySkill> reqs = oppSkills.findByOpportunityId(o.id);
        var elig = suitability.check(ctx.profile(), ctx.skillSet(), o, reqs);
        Set<String> have = ctx.skillSet();

        List<String> covered = reqs.stream().filter(s -> have.contains(s.skill)).map(s -> s.skill).distinct().sorted().toList();
        List<String> mandatoryGaps = reqs.stream().filter(s -> s.mandatory && !have.contains(s.skill)).map(s -> s.skill).distinct().sorted().toList();
        List<String> preferredGaps = reqs.stream().filter(s -> !s.mandatory && !have.contains(s.skill)).map(s -> s.skill).distinct().sorted().toList();
        double readiness = reqs.isEmpty() ? 100 : r1(covered.size() * 100.0 / reqs.size());

        String summary;
        if (!elig.eligible()) {
            summary = mandatoryGaps.isEmpty()
                    ? "You are not eligible for " + o.title + " for a non-skill reason — see the eligibility list above."
                    : "You are not eligible yet: " + mandatoryGaps.size() + " required skill"
                          + (mandatoryGaps.size() == 1 ? "" : "s") + " missing ("
                          + String.join(", ", mandatoryGaps) + "). Closing a required gap is what changes eligibility.";
        } else if (preferredGaps.isEmpty()) {
            summary = "You cover every listed skill for this internship. Nothing further to add here.";
        } else {
            summary = covered.size() + " of " + reqs.size() + " listed skills covered. "
                    + preferredGaps.size() + " improvement" + (preferredGaps.size() == 1 ? "" : "s")
                    + " could strengthen your fit: " + String.join(", ", preferredGaps) + ".";
        }
        return new SkillGap(oppInfo(o, reqs), elig.eligible(), covered, mandatoryGaps, preferredGaps,
                readiness, summary);
    }

    // ------------------------------------------------------------------
    // Grounded skill-gap assistant
    // ------------------------------------------------------------------

    public static final Set<String> GUIDANCE_QUESTIONS = Set.of("why_skill", "weak_area", "learn_first", "improve_impact");

    public Guidance guidance(AuthUser me, Long opportunityId, String question, String skill) {
        if (question == null || !GUIDANCE_QUESTIONS.contains(question)) {
            throw ApiException.badRequest("Only grounded skill-gap questions are supported.");
        }
        if (skill != null && !skill.isBlank() && !SkillTaxonomy.CANONICAL.contains(skill.trim())) {
            throw ApiException.badRequest("Choose a skill from the opportunity's listed skills.");
        }
        Ctx ctx = context(me);
        Opportunity o = requireOpp(opportunityId);
        List<OpportunitySkill> reqs = oppSkills.findByOpportunityId(o.id);
        Policy pol = activePolicy();
        var elig = suitability.check(ctx.profile(), ctx.skillSet(), o, reqs);
        var score = suitability.score(ctx.profile(), ctx.skillSet(), ctx.interestSet(),
                ctx.prefRanks().get(o.id), o, reqs, pol.weights());

        double[] fits = { score.skills(), score.qualification(), score.interest(),
                score.location(), score.preference(), score.learning(), score.experience() };
        double[] weights = { pol.weights().skills(), pol.weights().qualification(), pol.weights().interest(),
                pol.weights().location(), pol.weights().preference(), pol.weights().learning(), pol.weights().experience() };
        String[] keys = { "skills", "qualification", "interest", "location", "preference", "learning", "experience" };
        String[] labels = { "Skills match", "Qualification", "Interest fit", "Location match",
                "Preference alignment", "Learning potential", "Experience" };
        List<AiServiceClient.GapFactor> factors = new ArrayList<>();
        for (int i = 0; i < fits.length; i++) {
            double contribution = r2(fits[i] * weights[i] / 100.0);
            factors.add(new AiServiceClient.GapFactor(keys[i], labels[i], fits[i], weights[i],
                    contribution, r2(weights[i] - contribution)));
        }

        var req = new AiServiceClient.GapGuidanceRequest(question,
                skill == null ? null : skill.trim(), o.title, Labels.label(o.sector),
                reqs.stream().filter(s -> s.mandatory).map(s -> s.skill).sorted().toList(),
                reqs.stream().filter(s -> !s.mandatory).map(s -> s.skill).sorted().toList(),
                new ArrayList<>(ctx.skillSet()), elig.eligible(), factors, r1(score.overall()));
        try {
            var res = ai.gapGuidance(req);
            return new Guidance(res.answer(), res.engine(), res.grounding(), true);
        } catch (Exception e) {
            return new Guidance(fallbackGuidance(question, skill, o, reqs, factors),
                    "deterministic-fallback",
                    "structured opportunity requirements + your verified profile (AI service offline)", false);
        }
    }

    private String fallbackGuidance(String question, String skill, Opportunity o,
                                    List<OpportunitySkill> reqs, List<AiServiceClient.GapFactor> factors) {
        // Deliberately simple, honest and grounded: built only from the
        // opportunity's stated requirements and the live score breakdown.
        return switch (question) {
            case "why_skill" -> (skill == null ? "Pick a listed skill to see its role in " :
                    (reqs.stream().anyMatch(s -> s.skill.equals(skill) && s.mandatory)
                            ? skill + " is a required skill for "
                            : skill + " is a preferred skill for "))
                    + o.title + ". Required skills are part of the eligibility gate; preferred skills raise the skills factor.";
            case "weak_area" -> {
                factors.sort(Comparator.comparingDouble(AiServiceClient.GapFactor::lostPoints).reversed());
                if (factors.get(0).lostPoints() < 0.5) yield "No single factor is leaving meaningful points on the table here.";
                yield "Your weakest lever for " + o.title + " is " + factors.get(0).label()
                        + " (about " + r1(factors.get(0).lostPoints()) + " points unused).";
            }
            case "learn_first" -> {
                List<String> mand = reqs.stream().filter(s -> s.mandatory).map(s -> s.skill).toList();
                yield mand.isEmpty()
                        ? "You are eligible; a preferred skill would raise your skills factor."
                        : "Start with " + mand.get(0) + " — it is required for " + o.title + ".";
            }
            case "improve_impact" -> "Adding a listed skill would raise your skills factor, which is weighted "
                    + r0(weightOf(factors, "skills")) + "%; required-skill gaps change eligibility itself.";
            default -> "Grounded guidance covers: why a skill matters, where you are weak, what to learn first, and how an improvement moves your fit.";
        };
    }

    private static double weightOf(List<AiServiceClient.GapFactor> factors, String key) {
        return factors.stream().filter(f -> f.key().equals(key)).mapToDouble(AiServiceClient.GapFactor::weight).findFirst().orElse(0);
    }

    private static double r1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double r2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static double r0(double v) { return Math.round(v); }
}

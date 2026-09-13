package in.pragati.web;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import in.pragati.common.ApiException;
import in.pragati.common.Labels;
import in.pragati.domain.CandidateProfile;
import in.pragati.domain.Opportunity;
import in.pragati.domain.OpportunitySkill;
import in.pragati.domain.enums.CandidateState;
import in.pragati.domain.enums.OppStatus;
import in.pragati.domain.enums.Sector;
import in.pragati.repo.CandidateProfileRepository;
import in.pragati.repo.CandidateSkillRepository;
import in.pragati.repo.CandidatePreferenceRepository;
import in.pragati.repo.OpportunityRepository;
import in.pragati.repo.OpportunitySkillRepository;
import in.pragati.security.AuthUser;
import in.pragati.service.CandidateAnalyticsService;
import in.pragati.service.SuitabilityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Opportunities", description = "Browse internship opportunities with filters and eligibility.")
public class OpportunityController {

    public record OppCard(Long id, String title, String provider, String sector, String state,
                          String city, int capacity, int durationMonths, String minQualification,
                          String status, List<String> mandatorySkills, List<String> niceSkills,
                          boolean eligible, List<String> eligibilityReasons,
                          Integer myPreferenceRank, Double fitScore) { }

    public record OppDetail(OppCard card, String description) { }

    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository oppSkills;
    private final CandidateProfileRepository profiles;
    private final CandidateSkillRepository skills;
    private final CandidatePreferenceRepository preferences;
    private final SuitabilityService suitability;
    private final CandidateAnalyticsService analytics;

    public OpportunityController(OpportunityRepository opportunities, OpportunitySkillRepository oppSkills,
                                 CandidateProfileRepository profiles, CandidateSkillRepository skills,
                                 CandidatePreferenceRepository preferences, SuitabilityService suitability,
                                 CandidateAnalyticsService analytics) {
        this.opportunities = opportunities; this.oppSkills = oppSkills; this.profiles = profiles;
        this.skills = skills; this.preferences = preferences; this.suitability = suitability;
        this.analytics = analytics;
    }

    /** Same engine, same weights as Fit Analytics — never a second scoring formula.
     *  Returns null (never breaks the listing) when the candidate has no profile yet
     *  or the score cannot be computed for some other reason. */
    private Double fitScoreFor(AuthUser me, Long opportunityId) {
        if (me == null) return null;
        try {
            return analytics.fit(me, opportunityId).overallScore();
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/api/opportunities")
    @Operation(summary = "Explore opportunities (state, sector, qualification, skill, text search).")
    public Page<OppCard> explorer(@AuthenticationPrincipal AuthUser me,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "12") int size,
                                  @RequestParam(required = false) String state,
                                  @RequestParam(required = false) String sector,
                                  @RequestParam(required = false) String qualification,
                                  @RequestParam(required = false) String skill,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "true") boolean availableOnly) {
        CandidateProfile meProfile = me == null ? null
                : profiles.findByUserId(me.id()).orElse(null);
        Set<String> mySkills = meProfile == null ? Set.of() : skills.findByCandidateId(meProfile.id).stream()
                .filter(s -> s.validated).map(s -> s.canonical).collect(java.util.stream.Collectors.toSet());

        List<Opportunity> all = availableOnly
                ? opportunities.findAllByStatus(OppStatus.ACTIVE)
                : opportunities.findAll();
        if (state != null && !state.isBlank()) {
            all = all.stream().filter(o -> o.state.equalsIgnoreCase(state)).toList();
        }
        if (sector != null && !sector.isBlank()) {
            Sector s = parseSector(sector);
            all = all.stream().filter(o -> o.sector == s).toList();
        }
        if (qualification != null && !qualification.isBlank()) {
            all = all.stream().filter(o -> o.minQualification.name().equals(qualification)).toList();
        }
        if (skill != null && !skill.isBlank()) {
            List<Long> ids = all.stream().map(o -> o.id).toList();
            Set<Long> withSkill = oppSkills.findByOpportunityIdIn(ids).stream()
                    .filter(s -> s.skill.equalsIgnoreCase(skill))
                    .map(s -> s.opportunityId).collect(java.util.stream.Collectors.toSet());
            all = all.stream().filter(o -> withSkill.contains(o.id)).toList();
        }
        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase();
            all = all.stream().filter(o -> o.title.toLowerCase().contains(needle)
                    || (o.description != null && o.description.toLowerCase().contains(needle))).toList();
        }
        all = all.stream().sorted(java.util.Comparator.comparing((Opportunity o) -> o.id).reversed()).toList();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<Opportunity> slice = all.subList(from, to);
        List<Long> ids = slice.stream().map(o -> o.id).toList();
        Map<Long, List<OpportunitySkill>> req = new HashMap<>();
        for (OpportunitySkill s : oppSkills.findByOpportunityIdIn(ids)) {
            req.computeIfAbsent(s.opportunityId, k -> new java.util.ArrayList<>()).add(s);
        }
        Map<Long, Integer> myPrefs = meProfile == null ? Map.of() : preferences.findByCandidateId(meProfile.id).stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.opportunityId, p -> p.rank, (a, b) -> a));
        List<OppCard> cards = slice.stream().map(o -> {
            List<OpportunitySkill> rs = req.getOrDefault(o.id, List.of());
            SuitabilityService.EligibilityResult res = meProfile == null
                    ? new SuitabilityService.EligibilityResult(true, List.of())
                    : suitability.check(meProfile, mySkills, o, rs);
            List<String> mandatory = rs.stream().filter(s -> s.mandatory).map(s -> s.skill).toList();
            List<String> nice = rs.stream().filter(s -> !s.mandatory).map(s -> s.skill).toList();
            return new OppCard(o.id, o.title, "Provider", Labels.label(o.sector), o.state, o.city,
                    o.capacity, o.durationMonths, Labels.label(o.minQualification), Labels.label(o.status),
                    mandatory, nice, res.eligible(), res.reasons(), myPrefs.get(o.id), fitScoreFor(me, o.id));
        }).toList();
        return new org.springframework.data.domain.PageImpl<>(cards, PageRequest.of(page, size), all.size());
    }

    @GetMapping("/api/opportunities/{id}")
    @Operation(summary = "Opportunity detail, with the candidate's real eligibility (same rules as the explorer).")
    public OppDetail detail(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Opportunity o = opportunities.findById(id)
                .orElseThrow(() -> ApiException.notFound("Opportunity not found."));
        List<OpportunitySkill> rs = oppSkills.findByOpportunityId(o.id);
        List<String> mandatory = rs.stream().filter(s -> s.mandatory).map(s -> s.skill).toList();
        List<String> nice = rs.stream().filter(s -> !s.mandatory).map(s -> s.skill).toList();

        CandidateProfile meProfile = me == null ? null : profiles.findByUserId(me.id()).orElse(null);
        boolean eligible = true;
        List<String> reasons = List.of();
        Integer myRank = null;
        if (meProfile != null) {
            Set<String> mySkills = skills.findByCandidateId(meProfile.id).stream()
                    .filter(s -> s.validated).map(s -> s.canonical)
                    .collect(java.util.stream.Collectors.toSet());
            SuitabilityService.EligibilityResult res = suitability.check(meProfile, mySkills, o, rs);
            eligible = res.eligible();
            reasons = res.reasons();
            myRank = preferences.findByCandidateId(meProfile.id).stream()
                    .filter(x -> x.opportunityId.equals(o.id)).map(x -> x.rank).findFirst().orElse(null);
        }
        OppCard card = new OppCard(o.id, o.title, "Provider", Labels.label(o.sector), o.state,
                o.city, o.capacity, o.durationMonths, Labels.label(o.minQualification),
                Labels.label(o.status), mandatory, nice, eligible, reasons, myRank, fitScoreFor(me, o.id));
        return new OppDetail(card, o.description);
    }

    private Sector parseSector(String s) {
        try {
            return Sector.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid sector filter.");
        }
    }
}

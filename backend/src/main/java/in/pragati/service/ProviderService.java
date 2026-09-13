package in.pragati.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.pragati.common.ApiException;
import in.pragati.common.Labels;
import in.pragati.domain.*;
import in.pragati.domain.enums.*;
import in.pragati.repo.*;
import in.pragati.security.AuthUser;

/**
 * Provider services: organisation, owned-opportunity CRUD (object-level
 * ownership checks), capacity & demand, allocation impact.
 */
@Service
public class ProviderService {

    private final UserRepository users;
    private final ProviderRepository providers;
    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository oppSkills;
    private final CandidateProfileRepository profiles;
    private final CandidateSkillRepository skills;
    private final CandidatePreferenceRepository preferences;
    private final AssignmentRepository assignments;
    private final AllocationRunRepository runs;
    private final SuitabilityService suitability;
    private final AuditService audit;

    public ProviderService(UserRepository users, ProviderRepository providers,
                           OpportunityRepository opportunities, OpportunitySkillRepository oppSkills,
                           CandidateProfileRepository profiles, CandidateSkillRepository skills,
                           CandidatePreferenceRepository preferences, AssignmentRepository assignments,
                           AllocationRunRepository runs, SuitabilityService suitability, AuditService audit) {
        this.users = users; this.providers = providers; this.opportunities = opportunities;
        this.oppSkills = oppSkills; this.profiles = profiles; this.skills = skills;
        this.preferences = preferences; this.assignments = assignments;
        this.runs = runs; this.suitability = suitability; this.audit = audit;
    }

    public Provider requireOwnOrg(AuthUser me) {
        return providers.findByUserId(me.id())
                .orElseThrow(() -> ApiException.notFound("No organisation is linked to your account."));
    }

    public record OrgView(String orgName, String orgType, String state, String about,
                          int opportunityCount, int totalSeats, int activeCount) { }

    public OrgView org(AuthUser me) {
        Provider org = requireOwnOrg(me);
        List<Opportunity> mine = opportunities.findByProviderId(org.id);
        int seats = mine.stream().mapToInt(o -> o.capacity).sum();
        int active = (int) mine.stream().filter(o -> o.status == OppStatus.ACTIVE).count();
        return new OrgView(org.orgName, org.orgType, org.state, org.about,
                mine.size(), seats, active);
    }

    public record OppCard(Long id, String title, String sector, String state, String city,
                          int capacity, int durationMonths, String minQualification,
                          String status, int eligibleCount, int preferenceCount,
                          int allocatedCount, List<String> mandatorySkills, List<String> niceSkills,
                          String description) { }

    public Page<OppCard> myOpportunities(AuthUser me, int page, int size) {
        Provider org = requireOwnOrg(me);
        List<Opportunity> all = opportunities.findByProviderId(org.id);
        all.sort(java.util.Comparator.comparing((Opportunity o) -> o.id).reversed());
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        Page<Opportunity> p = new org.springframework.data.domain.PageImpl<>(
                all.subList(from, to), PageRequest.of(page, size), all.size());
        return mapCards(p);
    }

    private Page<OppCard> mapCards(Page<Opportunity> p) {
        List<OppCard> cards = new ArrayList<>();
        Map<Long, Integer> eligByOpp = eligibleCounts();
        Map<Long, Integer> prefByOpp = preferenceCounts();
        Map<Long, Integer> allocByOpp = allocatedInLatestRun();
        Map<Long, List<OpportunitySkill>> skillsByOpp = new HashMap<>();
        for (OpportunitySkill s : oppSkills.findByOpportunityIdIn(p.getContent().stream().map(o -> o.id).toList())) {
            skillsByOpp.computeIfAbsent(s.opportunityId, k -> new ArrayList<>()).add(s);
        }
        for (Opportunity o : p.getContent()) {
            List<OpportunitySkill> rs = skillsByOpp.getOrDefault(o.id, List.of());
            cards.add(new OppCard(o.id, o.title, Labels.label(o.sector), o.state, o.city,
                    o.capacity, o.durationMonths, Labels.label(o.minQualification),
                    Labels.label(o.status),
                    eligByOpp.getOrDefault(o.id, 0),
                    prefByOpp.getOrDefault(o.id, 0),
                    allocByOpp.getOrDefault(o.id, 0),
                    rs.stream().filter(s -> s.mandatory).map(s -> s.skill).sorted().toList(),
                    rs.stream().filter(s -> !s.mandatory).map(s -> s.skill).sorted().toList(),
                    o.description));
        }
        return new org.springframework.data.domain.PageImpl<>(cards, p.getPageable(), p.getTotalElements());
    }

    private Map<Long, Integer> eligibleCounts() {
        List<CandidateProfile> cands = profiles.findAllByCandidateState(CandidateState.ACTIVE);
        List<Long> ids = cands.stream().map(p -> p.id).toList();
        Map<Long, Set<String>> skillsMap = new HashMap<>();
        for (CandidateSkill s : skills.findByCandidateIdIn(ids)) {
            if (s.validated) skillsMap.computeIfAbsent(s.candidateId, k -> new HashSet<>()).add(s.canonical);
        }
        List<Opportunity> opps = opportunities.findAllByStatus(OppStatus.ACTIVE);
        List<Long> oppIds = opps.stream().map(o -> o.id).toList();
        Map<Long, List<OpportunitySkill>> reqMap = new HashMap<>();
        for (OpportunitySkill s : oppSkills.findByOpportunityIdIn(oppIds)) {
            reqMap.computeIfAbsent(s.opportunityId, k -> new ArrayList<>()).add(s);
        }
        Map<Long, Integer> out = new HashMap<>();
        for (Opportunity o : opps) {
            int count = 0;
            for (CandidateProfile c : cands) {
                if (suitability.check(c, skillsMap.getOrDefault(c.id, Set.of()),
                        o, reqMap.getOrDefault(o.id, List.of())).eligible()) count++;
            }
            out.put(o.id, count);
        }
        return out;
    }

    private Map<Long, Integer> preferenceCounts() {
        Map<Long, Integer> out = new HashMap<>();
        for (CandidatePreference p : preferences.findAll()) {
            out.merge(p.opportunityId, 1, Integer::sum);
        }
        return out;
    }

    private Map<Long, Integer> allocatedInLatestRun() {
        Map<Long, Integer> out = new HashMap<>();
        runs.findTopByOrderByIdDesc().filter(r -> r.status == RunStatus.COMPLETED).ifPresent(r -> {
            for (Assignment a : assignments.findByRunIdAndSource(r.id, AssignmentSource.GLOBAL)) {
                out.merge(a.opportunityId, 1, Integer::sum);
            }
        });
        return out;
    }

    public record OppInput(String title, String sector, String state, String city,
                           int capacity, int durationMonths, String minQualification,
                           String description, List<String> mandatorySkills, List<String> niceSkills) { }

    public Opportunity requireOwned(AuthUser me, Long id) {
        Provider org = requireOwnOrg(me);
        return opportunities.findByIdAndProviderId(id, org.id)
                .orElseThrow(() -> ApiException.notFound("Opportunity not found."));
    }

    public OppCard myOpportunity(AuthUser me, Long id) {
        return mapCards(new org.springframework.data.domain.PageImpl<>(
                List.of(requireOwned(me, id)),
                PageRequest.of(0, 1), 1)).getContent().get(0);
    }

    @Transactional
    public OppCard createOpp(AuthUser me, OppInput in) {
        Provider org = requireOwnOrg(me);
        Opportunity o = new Opportunity();
        applyInput(o, in);
        o.providerId = org.id;
        Opportunity saved = opportunities.save(o);
        audit.log(me, AuditAction.OPPORTUNITY_CREATED, "Opportunity", String.valueOf(saved.id),
                "Opportunity '" + saved.title + "' created by " + org.orgName + ".", null);
        return myOpportunity(me, saved.id);
    }

    @Transactional
    public OppCard updateOpp(AuthUser me, Long id, OppInput in) {
        Opportunity o = requireOwned(me, id);
        applyInput(o, in);
        o.touch();
        oppSkills.deleteByOpportunityId(o.id);
        addOppSkills(o, in);
        opportunities.save(o);
        audit.log(me, AuditAction.OPPORTUNITY_UPDATED, "Opportunity", String.valueOf(o.id),
                "Opportunity '" + o.title + "' updated.", null);
        return myOpportunity(me, o.id);
    }

    @Transactional
    public OppCard changeStatus(AuthUser me, Long id, String status) {
        Opportunity o = requireOwned(me, id);
        OppStatus target;
        try {
            target = OppStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid status.");
        }
        o.status = target;
        o.touch();
        opportunities.save(o);
        audit.log(me, AuditAction.OPPORTUNITY_STATUS_CHANGED, "Opportunity", String.valueOf(o.id),
                "Opportunity '" + o.title + "' is now " + Labels.label(target) + ".", null);
        return myOpportunity(me, o.id);
    }

    private void applyInput(Opportunity o, OppInput in) {
        if (in.title() == null || in.title().isBlank() || in.title().length() > 160) {
            throw ApiException.badRequest("Title is required (max 160 characters).");
        }
        o.title = in.title();
        o.sector = parseEnum(Sector.class, in.sector(), "sector");
        if (in.state() == null || in.state().isBlank()) throw ApiException.badRequest("State is required.");
        o.state = in.state();
        o.city = in.city();
        if (in.capacity() < 1 || in.capacity() > 500) throw ApiException.badRequest("Capacity must be between 1 and 500.");
        o.capacity = in.capacity();
        if (in.durationMonths() < 1 || in.durationMonths() > 24) throw ApiException.badRequest("Duration must be 1-24 months.");
        o.durationMonths = in.durationMonths();
        o.minQualification = parseEnum(Qualification.class, in.minQualification(), "minimum qualification");
        o.description = in.description() == null ? "" : in.description();
        if (in.description() != null && in.description().length() > 800) {
            throw ApiException.badRequest("Keep the description under 800 characters.");
        }
    }

    private void addOppSkills(Opportunity o, OppInput in) {
        Set<String> mandatory = new HashSet<>();
        if (in.mandatorySkills() != null) {
            for (String s : in.mandatorySkills()) {
                String c = SkillTaxonomy.normalize(s);
                if (c == null) throw ApiException.badRequest("Unrecognized skill: " + s);
                mandatory.add(c);
            }
        }
        if (mandatory.isEmpty()) throw ApiException.badRequest("Select at least one required skill.");
        if (mandatory.size() > 5) throw ApiException.badRequest("Use at most 5 required skills.");
        for (String s : mandatory) {
            OpportunitySkill os = new OpportunitySkill();
            os.opportunityId = o.id;
            os.skill = s;
            os.mandatory = true;
            oppSkills.save(os);
        }
        Set<String> nice = new HashSet<>();
        if (in.niceSkills() != null) {
            for (String s : in.niceSkills()) {
                String c = SkillTaxonomy.normalize(s);
                if (c == null) throw ApiException.badRequest("Unrecognized skill: " + s);
                nice.add(c);
            }
        }
        nice.removeAll(mandatory);
        if (nice.size() > 5) throw ApiException.badRequest("Use at most 5 additional skills.");
        for (String s : nice) {
            OpportunitySkill os = new OpportunitySkill();
            os.opportunityId = o.id;
            os.skill = s;
            os.mandatory = false;
            oppSkills.save(os);
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) throw ApiException.badRequest(field + " is required.");
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid value for " + field + ".");
        }
    }

    public record CapacityEntry(Long opportunityId, String title, int capacity, int eligible,
                                int preferences, double pressure) { }

    public List<CapacityEntry> capacity(AuthUser me) {
        Provider org = requireOwnOrg(me);
        List<Opportunity> mine = opportunities.findByProviderId(org.id);
        Map<Long, Integer> elig = eligibleCounts();
        Map<Long, Integer> pref = preferenceCounts();
        List<CapacityEntry> out = new ArrayList<>();
        for (Opportunity o : mine) {
            int e = elig.getOrDefault(o.id, 0);
            double pressure = o.capacity > 0 ? Math.round(e * 1000.0 / o.capacity) / 1000.0 : 0;
            out.add(new CapacityEntry(o.id, o.title, o.capacity, e,
                    pref.getOrDefault(o.id, 0), pressure));
        }
        return out;
    }

    public record ImpactEntry(Long opportunityId, String title, int capacity, int allocated,
                              int unmetDemand, double avgSuitability) { }

    public List<ImpactEntry> impact(AuthUser me) {
        Provider org = requireOwnOrg(me);
        List<Opportunity> mine = opportunities.findByProviderId(org.id);
        Map<Long, List<Assignment>> byOpp = new HashMap<>();
        runs.findTopByOrderByIdDesc().filter(r -> r.status == RunStatus.COMPLETED).ifPresent(r -> {
            for (Assignment a : assignments.findByRunIdAndSource(r.id, AssignmentSource.GLOBAL)) {
                byOpp.computeIfAbsent(a.opportunityId, k -> new ArrayList<>()).add(a);
            }
        });
        Map<Long, Integer> elig = eligibleCounts();
        List<ImpactEntry> out = new ArrayList<>();
        for (Opportunity o : mine) {
            List<Assignment> a = byOpp.getOrDefault(o.id, List.of());
            double avg = a.isEmpty() ? 0 : Math.round(a.stream().mapToDouble(x -> x.suitability).average().orElse(0) * 10.0) / 10.0;
            int e = elig.getOrDefault(o.id, 0);
            out.add(new ImpactEntry(o.id, o.title, o.capacity, a.size(),
                    Math.max(0, e - o.capacity), avg));
        }
        return out;
    }
}

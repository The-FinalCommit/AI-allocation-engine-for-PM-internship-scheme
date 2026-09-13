package in.pragati.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import in.pragati.common.ApiException;
import in.pragati.domain.*;
import in.pragati.domain.enums.AssignmentSource;
import in.pragati.repo.*;

/**
 * Strategy comparison (PRAGATI global vs sequential baseline), candidate
 * movement analysis and the "why this allocation" evidence view.
 */
@Service
public class ComparisonService {

    public record ComparisonDto(
            int globalAllocated, int baselineAllocated,
            double globalSuitability, double baselineSuitability,
            double globalPreferenceSatisfaction, double baselinePreferenceSatisfaction,
            double seatUtilization, long globalObjective, long baselineObjective,
            double globalSolverSeconds, String summary) { }

    public record MovementDto(Long candidateId, String candidateName,
                              String before, String after,
                              double beforeScore, double afterScore,
                              String status, String reason) { }

    public record WhyFactor(String key, String label, double fit, double weight, double contribution) { }

    public record WhyDto(Long candidateId, String candidateName,
                         Long opportunityId, String opportunityTitle, String sector, String state,
                         double suitability, List<WhyFactor> factors,
                         String narrative, String reason,
                         int eligibleCount, int seatCount, Integer preferenceRank,
                         Long runId, int runNumber) { }

    private final AllocationRunRepository runs;
    private final AssignmentRepository assignments;
    private final CandidateProfileRepository profiles;
    private final OpportunityRepository opportunities;
    private final CandidatePreferenceRepository preferences;
    private final ConflictMetricRepository conflicts;
    private final GroupMetricRepository groupMetrics;
    private final DatasetSnapshotRepository snapshotRepo;
    private final ObjectMapper json;

    public ComparisonService(AllocationRunRepository runs, AssignmentRepository assignments,
                             CandidateProfileRepository profiles, OpportunityRepository opportunities,
                             CandidatePreferenceRepository preferences, ConflictMetricRepository conflicts,
                             GroupMetricRepository groupMetrics, DatasetSnapshotRepository snapshotRepo,
                             ObjectMapper json) {
        this.runs = runs; this.assignments = assignments; this.profiles = profiles;
        this.opportunities = opportunities; this.preferences = preferences;
        this.conflicts = conflicts; this.groupMetrics = groupMetrics;
        this.snapshotRepo = snapshotRepo; this.json = json;
    }

    private Map<Long, Assignment> index(List<Assignment> list) {
        Map<Long, Assignment> m = new HashMap<>();
        for (Assignment a : list) m.put(a.candidateId, a);
        return m;
    }

    public ComparisonDto compare(Long runId) {
        AllocationRun run = runs.findById(runId).orElseThrow(() -> ApiException.notFound("Allocation run not found."));
        List<Assignment> global = assignments.findByRunIdAndSource(runId, AssignmentSource.GLOBAL);
        List<Assignment> baseline = assignments.findByRunIdAndSource(runId, AssignmentSource.BASELINE);
        Map<Long, Map<Long, Integer>> prefMap = preferenceMap(
                profiles.findAllByCandidateState(in.pragati.domain.enums.CandidateState.ACTIVE));

        long globalObj = run.objective == null ? 0 : run.objective;
        long baselineObj = baseline.stream().mapToLong(a -> 1000 + Math.round(a.suitability * 10)).sum();
        double globalSuit = global.isEmpty() ? 0 : r1(global.stream().mapToDouble(a -> a.suitability).average().orElse(0));
        double baselineSuit = baseline.isEmpty() ? 0 : r1(baseline.stream().mapToDouble(a -> a.suitability).average().orElse(0));
        double globalPref = prefSatisfaction(global, prefMap);
        double baselinePref = prefSatisfaction(baseline, prefMap);
        double utilization = seatUtilization(runId, global);

        String summary = buildSummary(global.size(), baseline.size(), globalSuit, baselineSuit, globalPref, baselinePref);

        return new ComparisonDto(global.size(), baseline.size(), globalSuit, baselineSuit,
                globalPref, baselinePref, utilization, globalObj, baselineObj,
                run.solverRuntimeMs / 1000.0, summary);
    }

    private String buildSummary(int gA, int bA, double gS, double bS, double gP, double bP) {
        StringBuilder sb = new StringBuilder();
        double dSuit = gS - bS;
        sb.append("The global allocation assigned ").append(gA).append(" candidates")
          .append(" (sequential baseline: ").append(bA).append(")");
        if (Math.abs(dSuit) >= 0.05) {
            sb.append(" with an average suitability of ").append(gS).append("%")
              .append(" versus ").append(bS).append("% for the sequential approach (")
              .append(dSuit > 0 ? "+" : "").append(r1(dSuit)).append(" points)");
        } else {
            sb.append(" with a similar average suitability (").append(gS).append("% vs ").append(bS).append("%)");
        }
        double dPref = gP - bP;
        if (Math.abs(dPref) >= 0.05) {
            sb.append(". Preference satisfaction: ").append(gP).append("% vs ")
              .append(bP).append("% (").append(dPref > 0 ? "+" : "").append(r1(dPref)).append(" points).");
        } else {
            sb.append(". Preference satisfaction was comparable (").append(gP).append("% vs ").append(bP).append("%).");
        }
        return sb.toString();
    }

    public Page<MovementDto> movements(Long runId, String statusFilter, int page, int size) {
        AllocationRun run = runs.findById(runId).orElseThrow(() -> ApiException.notFound("Allocation run not found."));
        List<Assignment> global = assignments.findByRunIdAndSource(runId, AssignmentSource.GLOBAL);
        List<Assignment> baseline = assignments.findByRunIdAndSource(runId, AssignmentSource.BASELINE);
        Map<Long, Assignment> g = index(global);
        Map<Long, Assignment> b = index(baseline);
        Map<Long, String> names = new HashMap<>();
        Map<Long, String> oppNames = new HashMap<>();
        for (Assignment a : global) {
            names.computeIfAbsent(a.candidateId, id -> profileName(id));
            oppNames.computeIfAbsent(a.opportunityId, this::opportunityName);
        }
        for (Assignment a : baseline) {
            names.computeIfAbsent(a.candidateId, id -> profileName(id));
            oppNames.computeIfAbsent(a.opportunityId, this::opportunityName);
        }
        List<MovementDto> all = new ArrayList<>();
        for (Long cand : new java.util.TreeSet<>(new java.util.ArrayList<>(g.keySet()))) {
            Assignment ga = g.get(cand);
            Assignment ba = b.get(cand);
            String status;
            if (ba == null) status = "ADDED";
            else if (!ba.opportunityId.equals(ga.opportunityId)) status = "MOVED";
            else status = "UNCHANGED";
            all.add(toMovement(cand, ba, ga, status, names, oppNames));
        }
        for (Long cand : new java.util.TreeSet<>(new java.util.ArrayList<>(b.keySet()))) {
            if (g.containsKey(cand)) continue;
            all.add(toMovement(cand, b.get(cand), null, "REMOVED", names, oppNames));
        }
        List<MovementDto> filtered = (statusFilter == null || statusFilter.isBlank())
                ? all : all.stream().filter(m -> m.status().equals(statusFilter)).toList();
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return new org.springframework.data.domain.PageImpl<>(filtered.subList(from, to),
                PageRequest.of(page, size), filtered.size());
    }

    private MovementDto toMovement(Long cand, Assignment before, Assignment after, String status,
                                   Map<Long, String> names, Map<Long, String> oppNames) {
        String beforeOpp = before != null ? oppNames.get(before.opportunityId) : "Not allocated";
        String afterOpp = after != null ? oppNames.get(after.opportunityId) : "Not allocated";
        double beforeScore = before != null ? before.suitability : 0;
        double afterScore = after != null ? after.suitability : 0;
        String reason = switch (status) {
            case "ADDED" -> "Allocated by the global allocation; no sequential-baseline assignment for this candidate.";
            case "REMOVED" -> "Not selected by the global allocation; the seat was used for a stronger overall fit.";
            case "MOVED" -> Math.abs(afterScore - beforeScore) >= 0.05
                    ? "Reassigned to improve overall system fit (" + r1(afterScore - beforeScore) + " points for this candidate)."
                    : "Reassigned to balance scarce seats across the candidate pool.";
            default -> "The same opportunity was selected by both strategies.";
        };
        return new MovementDto(cand, names.getOrDefault(cand, "Candidate"), beforeOpp, afterOpp,
                beforeScore, afterScore, status, reason);
    }

    public WhyDto why(Long runId, Long candidateId) {
        AllocationRun run = runs.findById(runId).orElseThrow(() -> ApiException.notFound("Allocation run not found."));
        Assignment a = assignments.findByRunIdAndCandidateIdAndSource(runId, candidateId, AssignmentSource.GLOBAL)
                .orElseThrow(() -> ApiException.notFound("This candidate has no assignment in this run."));
        CandidateProfile p = profiles.findById(candidateId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found."));
        Opportunity o = opportunities.findById(a.opportunityId)
                .orElseThrow(() -> ApiException.notFound("Opportunity not found."));
        List<WhyFactor> factors = parseFactors(run, a);
        int eligible = conflicts.findByRunId(runId).stream()
                .filter(c -> c.opportunityId.equals(a.opportunityId))
                .mapToInt(c -> c.eligibleCount).findFirst().orElse(0);
        Integer rank = preferences.findByCandidateId(candidateId).stream()
                .filter(x -> x.opportunityId.equals(a.opportunityId))
                .map(x -> x.rank).findFirst().orElse(null);
        String narrative = buildNarrative(p, o, factors, eligible, a, rank);
        return new WhyDto(p.id, p.fullName, o.id, o.title, in.pragati.common.Labels.label(o.sector),
                o.state, a.suitability, factors, narrative, a.reason, eligible, o.capacity, rank, run.id, run.number);
    }

    /** Factor-level explanations for the first `limit` global assignments of a run. */
    public List<WhyDto> whyAssigned(Long runId, int limit) {
        List<Assignment> gs = assignments.findByRunIdAndSource(runId, AssignmentSource.GLOBAL);
        List<WhyDto> out = new ArrayList<>();
        for (Assignment a : gs) {
            if (out.size() >= limit) break;
            try { out.add(why(runId, a.candidateId)); } catch (Exception ignored) { }
        }
        return out;
    }

    private String buildNarrative(CandidateProfile p, Opportunity o, List<WhyFactor> factors,
                                  int eligible, Assignment a, Integer rank) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.fullName).append(" was assigned to ").append(o.title).append(". ");
        if (rank != null) {
            sb.append("This was the candidate's ").append(rank).append(" stated preference. ");
        }
        factors.stream().limit(2).forEach(f ->
                sb.append(f.label()).append(" contributed ").append(f.contribution())
                  .append(" points (fit ").append(f.fit()).append("%, weight ")
                  .append(f.weight()).append("%). "));
        if (eligible > 0) {
            sb.append(eligible).append(" eligible candidates competed for ")
              .append(a == null ? 0 : o.capacity).append(" seats; the global allocation balanced these claims together rather than first-come-first-served.");
        }
        return sb.toString();
    }

    private List<WhyFactor> parseFactors(AllocationRun run, Assignment a) {
        List<WhyFactor> list = new ArrayList<>();
        try {
            JsonNode n = json.readTree(a.breakdownJson);
            String[] keys = {"skills", "qualification", "interest", "location", "preference", "learning", "experience"};
            String[] labels = {"Skills", "Qualification", "Interest", "Location", "Preference", "Learning Potential", "Experience"};
            for (int i = 0; i < keys.length; i++) {
                JsonNode f = n.path(keys[i]);
                list.add(new WhyFactor(keys[i], labels[i], f.path("fit").asDouble(),
                        f.path("weight").asDouble(), f.path("contribution").asDouble()));
            }
            list.sort((x, y) -> Double.compare(y.contribution(), x.contribution()));
        } catch (Exception e) {
            // leave empty; the UI still shows the raw fits
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private double prefSatisfaction(List<Assignment> list, Map<Long, Map<Long, Integer>> prefMap) {
        if (list.isEmpty()) return 0;
        long satisfied = list.stream()
                .filter(a -> prefMap.getOrDefault(a.candidateId, Map.of()).containsKey(a.opportunityId))
                .count();
        return r1(satisfied * 100.0 / list.size());
    }

    private double seatUtilization(Long runId, List<Assignment> global) {
        AllocationRun run = runs.findById(runId).orElseThrow(() -> ApiException.notFound("Allocation run not found."));
        DatasetSnapshot snap = snapshotRepo.findById(run.snapshotId).orElse(null);
        if (snap == null) return 0;
        long seats = snap.seatCount;
        if (run.overridesJson != null && !run.overridesJson.isBlank()) {
            try {
                JsonNode n = json.readTree(run.overridesJson);
                java.util.Iterator<java.util.Map.Entry<String, JsonNode>> it = n.path("capacityOverrides").fields();
                while (it.hasNext()) {
                    java.util.Map.Entry<String, JsonNode> e = it.next();
                    Opportunity o = opportunities.findById(Long.valueOf(e.getKey())).orElse(null);
                    if (o != null) seats = seats - o.capacity + e.getValue().asInt();
                }
                for (JsonNode v : n.path("withdrawnOpportunities")) {
                    Opportunity o = opportunities.findById(v.asLong()).orElse(null);
                    if (o != null) seats = Math.max(0, seats - o.capacity);
                }
            } catch (Exception ignored) { }
        }
        if (seats <= 0) return 0;
        return r1(global.size() * 100.0 / seats);
    }

    private Map<Long, Map<Long, Integer>> preferenceMap(List<CandidateProfile> candidates) {
        List<Long> ids = candidates.stream().map(p -> p.id).toList();
        Map<Long, Map<Long, Integer>> m = new HashMap<>();
        for (CandidatePreference p : preferences.findByCandidateIdIn(ids)) {
            m.computeIfAbsent(p.candidateId, k -> new HashMap<>()).put(p.opportunityId, p.rank);
        }
        return m;
    }

    private String profileName(Long id) {
        return profiles.findById(id).map(p -> p.fullName).orElse("Candidate");
    }

    private String opportunityName(Long id) {
        return opportunities.findById(id).map(o -> o.title).orElse("Opportunity");
    }

    private double r1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}

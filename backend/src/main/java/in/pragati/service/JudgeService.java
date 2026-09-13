package in.pragati.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import in.pragati.common.Labels;
import in.pragati.domain.*;
import in.pragati.domain.enums.*;
import in.pragati.repo.*;

/**
 * Judge Mode: the complete SIH narrative as 14 guided steps, each with
 * WHAT IS HAPPENING / WHY IT MATTERS plus live data from the real system.
 */
@Service
public class JudgeService {

    public record Step(int n, String title, String what, String why, Map<String, Object> data,
                       String path) { }

    private final DatasetSnapshotRepository snapshots;
    private final AllocationRunRepository runs;
    private final AssignmentRepository assignments;
    private final OpportunityRepository opportunities;
    private final CandidateProfileRepository profiles;
    private final GroupMetricRepository groupMetrics;
    private final ConflictMetricRepository conflicts;
    private final GeoMetricRepository geoMetrics;
    private final SimulationRunRepository simulations;
    private final ComparisonService comparison;
    private final OverviewService overview;

    public JudgeService(DatasetSnapshotRepository snapshots, AllocationRunRepository runs,
                        AssignmentRepository assignments, OpportunityRepository opportunities,
                        CandidateProfileRepository profiles, GroupMetricRepository groupMetrics,
                        ConflictMetricRepository conflicts, GeoMetricRepository geoMetrics,
                        SimulationRunRepository simulations, ComparisonService comparison,
                        OverviewService overview) {
        this.snapshots = snapshots; this.runs = runs; this.assignments = assignments;
        this.opportunities = opportunities; this.profiles = profiles;
        this.groupMetrics = groupMetrics; this.conflicts = conflicts;
        this.geoMetrics = geoMetrics; this.simulations = simulations;
        this.comparison = comparison; this.overview = overview;
    }

    public List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        DatasetSnapshot snap = snapshots.findTopByOrderByIdDesc().orElse(null);
        AllocationRun run = latestCompletedRun();
        String runPath = run != null ? "/admin/runs/" + run.id : null;
        int candidates = snap != null ? snap.candidateCount : 0;
        int opps = snap != null ? snap.opportunityCount : 0;
        int seats = snap != null ? snap.seatCount : 0;

        steps.add(new Step(1, "The problem",
                "India needs a fair, defensible way to allocate limited internship opportunities across a large candidate population — respecting eligibility, capacity, preferences, geography and policy.",
                "First-come-first-served allocation is slow, opaque and systematically favours the fast, not the best. A global, explainable allocation is the core of PRAGATI.",
                Map.of("sihProblem", "SIH25033 — AI Smart Allocation for PM Internship Scheme"), null));

        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("candidates", candidates);
        pool.put("scenario", snap != null ? Labels.label(snap.scenario) : "—");
        pool.put("note", "Synthetic demo data, deterministically generated.");
        steps.add(new Step(2, "The candidate pool",
                "PRAGATI knows every candidate: profile, qualification, validated skills, interests, preferences and location.",
                "Allocation quality is bounded by data quality. PRAGATI validates skills through a taxonomy and scores profile readiness.",
                pool, "/admin/technical"));

        pool.clear();
        pool.put("opportunities", opps);
        pool.put("totalSeats", seats);
        pool.put("averageSeats", opps > 0 ? Math.round(seats * 10.0 / opps) / 10.0 : 0);
        steps.add(new Step(3, "The opportunity pool",
                "Providers publish opportunities with sector, location, capacity, duration, minimum qualification and mandatory skills.",
                "Explicit requirements make eligibility a hard, auditable rule — not a judgement call.",
                pool, "/candidate/explore"));

        List<ConflictMetric> contested = run != null
                ? conflicts.findByRunId(run.id).stream().limit(5).toList() : List.of();
        Map<String, Object> scarcity = new LinkedHashMap<>();
        scarcity.put("demand", candidates);
        scarcity.put("seats", seats);
        scarcity.put("contestedOpportunities", contested.size());
        if (!contested.isEmpty()) {
            ConflictMetric c = contested.get(0);
            String title = opportunities.findById(c.opportunityId).map(o -> o.title).orElse("Opportunity");
            scarcity.put("example", title + ": " + c.eligibleCount + " eligible candidates for " + c.seatCount + " seats");
        }
        steps.add(new Step(4, "Scarce capacity",
                "Seats are fewer than eligible candidates. Multiple candidates compete for the same opportunity.",
                "Scarcity is where local, candidate-by-candidate decisions fail: an early claim can lock in a weaker overall outcome.",
                scarcity, run != null ? runPath + "?tab=moves" : null));

        Map<String, Object> baseline = new LinkedHashMap<>();
        if (run != null) {
            ComparisonService.ComparisonDto cmp = comparison.compare(run.id);
            baseline.put("baselineAllocated", cmp.baselineAllocated());
            baseline.put("baselineSuitability", cmp.baselineSuitability());
            baseline.put("baselinePreferenceSatisfaction", cmp.baselinePreferenceSatisfaction());
        }
        steps.add(new Step(5, "Sequential baseline",
                "A deterministic reference strategy allocates candidates one by one: each takes the best opportunity still available.",
                "This is the honest 'before' picture — the same data, eligibility and scoring, processed in order. It makes the comparison fair.",
                baseline, run != null ? runPath + "?tab=compare" : null));

        Map<String, Object> global = new LinkedHashMap<>();
        if (run != null) {
            ComparisonService.ComparisonDto cmp = comparison.compare(run.id);
            global.put("allocated", cmp.globalAllocated());
            global.put("suitability", cmp.globalSuitability());
            global.put("preferenceSatisfaction", cmp.globalPreferenceSatisfaction());
            global.put("seatUtilization", cmp.seatUtilization());
            global.put("objective", cmp.globalObjective());
            global.put("summary", cmp.summary());
        }
        steps.add(new Step(6, "Global allocation",
                "PRAGATI allocates the whole population together under one constraint system: eligibility, capacity, at most one seat per candidate and policy objectives.",
                "By optimising the system as a whole, PRAGATI can swap weaker early claims for stronger ones — improving the total outcome for everyone.",
                global, runPath));

        Map<String, Object> cmpData = new LinkedHashMap<>();
        if (run != null) {
            ComparisonService.ComparisonDto cmp = comparison.compare(run.id);
            cmpData.put("suitabilityDelta", Math.round((cmp.globalSuitability() - cmp.baselineSuitability()) * 10) / 10.0);
            cmpData.put("preferenceDelta", Math.round((cmp.globalPreferenceSatisfaction() - cmp.baselinePreferenceSatisfaction()) * 10) / 10.0);
            cmpData.put("summary", cmp.summary());
        }
        steps.add(new Step(7, "The comparison",
                "PRAGATI vs the sequential baseline on identical data: allocations, suitability, preference satisfaction and seat utilization.",
                "The delta is computed from stored results — never presented as a claim. Even small gains multiply across thousands of candidates.",
                cmpData, run != null ? runPath + "?tab=compare" : null));

        Map<String, Object> why = new LinkedHashMap<>();
        if (run != null) {
            assignments.findByRunIdAndSource(run.id, AssignmentSource.GLOBAL).stream().findFirst().ifPresent(a -> {
                String candName = profiles.findById(a.candidateId).map(p -> p.fullName).orElse("A candidate");
                String oppTitle = opportunities.findById(a.opportunityId).map(o -> o.title).orElse("an opportunity");
                why.put("exampleCandidate", candName);
                why.put("exampleOpportunity", oppTitle);
                why.put("suitability", a.suitability);
                why.put("note", "Every assignment stores fit, weight and contribution per factor.");
            });
        }
        steps.add(new Step(8, "Why this allocation",
                "Every allocation is explained with stored evidence: factor-level fit, weight and contribution, competition level and stated preference.",
                "Explainability turns an optimizer's output into a decision the user can understand — and an auditor can verify.",
                why, run != null ? runPath + "?tab=why" : null));

        Map<String, Object> fair = new LinkedHashMap<>();
        if (run != null) {
            groupMetrics.findByRunId(run.id).stream()
                    .filter(g -> g.groupType.equals("RURAL_URBAN")).forEach(g -> {
                        fair.put(g.groupValue, Map.of(
                                "population", g.population, "allocated", g.allocated,
                                "rate", g.allocationRate + "%",
                                "avgSuitability", g.avgSuitability + "%"));
                    });
        }
        steps.add(new Step(9, "Fairness outcomes",
                "PRAGATI measures allocation rates and suitability by group (rural/urban, state, qualification) for every run, and shows observed disparities in percentage points.",
                "Fairness is measured, not promised. Disparities are surfaced so administrators can see them and respond with policy.",
                fair, run != null ? runPath + "?tab=fairness" : null));

        Map<String, Object> geo = new LinkedHashMap<>();
        if (run != null) {
            long states = geoMetrics.findByRunId(run.id).size();
            geo.put("statesTracked", states);
            geoMetrics.findByRunId(run.id).stream()
                    .sorted((a, b) -> Integer.compare(b.unmetDemand, a.unmetDemand))
                    .limit(3)
                    .forEach(g -> geo.put(g.state, g.demand + " candidates / " + g.capacity + " seats"));
        }
        steps.add(new Step(10, "Geography",
                "Demand, capacity, allocation and unmet demand are aggregated by state and shown on a map and table that always agree.",
                "Geography shows where opportunity is scarce — the input administrators need for targeted policy.",
                geo, run != null ? runPath + "?tab=geo" : null));

        Map<String, Object> sim = new LinkedHashMap<>();
        long sims = simulations.count();
        sim.put("simulationsRun", sims);
        steps.add(new Step(11, "Policy simulation",
                "Policy Lab changes weights (skills, preference, location, …) and runs the real optimizer in a sandbox, comparing current vs scenario outcomes.",
                "Policymakers can see trade-offs before committing. Production allocations are never touched by a simulation.",
                sim, "/admin/whatif"));

        steps.add(new Step(12, "Infeasibility",
                "When a configuration cannot be satisfied (for example: every candidate must be allocated but seats are fewer), the real solver returns INFEASIBLE — and PRAGATI explains the conflict and possible resolutions.",
                "Honest infeasibility is a feature: it prevents administrators from unknowingly shipping impossible policies.",
                Map.of("demonstration", "Load the Infeasibility Demo scenario and run the Full Coverage policy"), null));

        steps.add(new Step(13, "Reallocation",
                "When operations change — capacity, withdrawals, policy — PRAGATI re-optimizes from the parent allocation and shows exactly who moved, who was added or removed, and why.",
                "Life changes after every allocation. A transparent diff keeps trust when seats move.",
                Map.of(), "/admin/reallocation"));

        Map<String, Object> prov = new LinkedHashMap<>();
        if (run != null) {
            prov.put("run", "#" + run.number + " (" + run.runCode + ")");
            prov.put("policy", run.policyName + " v" + run.policyVersion);
            prov.put("dataset", snap != null ? snap.fingerprint : "—");
            prov.put("solver", "OR-Tools CP-SAT · " + (run.solverRuntimeMs / 1000.0) + "s");
        }
        steps.add(new Step(14, "Provenance & reproducibility",
                "Every result stores the exact dataset version, policy, weights, optimizer, seed and runtime. Decision History keeps every run reproducible.",
                "An allocation is only as defensible as its paper trail. PRAGATI's paper trail is complete and machine-readable.",
                prov, "/admin/runs"));
        return steps;
    }

    private AllocationRun latestCompletedRun() {
        return runs.findTopByOrderByIdDesc()
                .filter(r -> r.status == RunStatus.COMPLETED)
                .orElse(null);
    }
}

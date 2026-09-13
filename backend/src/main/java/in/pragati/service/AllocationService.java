package in.pragati.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import in.pragati.ai.AiServiceClient;
import in.pragati.common.ApiException;
import in.pragati.common.Labels;
import in.pragati.common.Weights;
import in.pragati.config.AppProperties;
import in.pragati.domain.*;
import in.pragati.domain.enums.*;
import in.pragati.repo.*;
import in.pragati.security.AuthUser;
import in.pragati.service.SuitabilityService.EligiblePair;
import in.pragati.service.SuitabilityService.PairScore;

/**
 * Orchestrates allocation runs end-to-end:
 * dataset snapshot → eligibility → suitability → OR-Tools CP-SAT (global)
 * + deterministic sequential baseline → fairness/geography/conflict metrics
 * → persisted, provenance-complete results.
 */
@Service
public class AllocationService {

    private static final Logger log = LoggerFactory.getLogger(AllocationService.class);

    private final AllocationRunRepository runs;
    private final DatasetSnapshotRepository snapshots;
    private final CandidateProfileRepository profiles;
    private final CandidateSkillRepository skills;
    private final CandidateInterestRepository interests;
    private final CandidatePreferenceRepository preferences;
    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository oppSkills;
    private final AssignmentRepository assignments;
    private final PolicyPresetRepository presets;
    private final AiServiceClient ai;
    private final MetricsService metrics;
    private final AuditService audit;
    private final ScenarioService scenarios;
    private final SuitabilityService suitability;
    private final ObjectMapper json;
    private final AppProperties props;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "pragati-allocation");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean runActive = new AtomicBoolean(false);

    public AllocationService(AllocationRunRepository runs, DatasetSnapshotRepository snapshots,
                             CandidateProfileRepository profiles, CandidateSkillRepository skills,
                             CandidateInterestRepository interests, CandidatePreferenceRepository preferences,
                             OpportunityRepository opportunities, OpportunitySkillRepository oppSkills,
                             AssignmentRepository assignments, PolicyPresetRepository presets,
                             AiServiceClient ai, MetricsService metrics, AuditService audit,
                             ScenarioService scenarios, SuitabilityService suitability,
                             ObjectMapper json, AppProperties props) {
        this.runs = runs; this.snapshots = snapshots; this.profiles = profiles;
        this.skills = skills; this.interests = interests; this.preferences = preferences;
        this.opportunities = opportunities; this.oppSkills = oppSkills;
        this.assignments = assignments; this.presets = presets; this.ai = ai;
        this.metrics = metrics; this.audit = audit; this.scenarios = scenarios;
        this.suitability = suitability; this.json = json; this.props = props;
    }

    public record StartRunRequest(String policyKey, Map<String, Double> weights,
                                  Boolean fullCoverage, Integer fairnessFloorPct) { }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public boolean isRunActive() {
        return runActive.get();
    }

    public List<AllocationRun> activeRuns() {
        return runs.findAllByStatusIn(List.of(RunStatus.QUEUED, RunStatus.RUNNING));
    }

    public AllocationRun startRun(StartRunRequest req, AuthUser actor) {
        scenarios.recoverStaleRuns();
        if (!runActive.compareAndSet(false, true)) {
            throw ApiException.conflict("An allocation is already in progress. Please wait for it to complete.");
        }
        try {
            AllocationRun run = prepareRun(req, null, null, actor);
            executor.submit(() -> execute(run.id));
            return run;
        } catch (RuntimeException e) {
            runActive.set(false);
            throw e;
        }
    }

    /** Creates a configured run (used by both standard runs and reallocation). */
    public AllocationRun prepareRun(StartRunRequest req, Long parentRunId,
                                    String overridesJson, AuthUser actor) {
        scenarios.ensureNoActiveRun();
        DatasetSnapshot snap = scenarios.currentSnapshot()
                .orElseThrow(() -> ApiException.notFound("No scenario is loaded yet. Load a scenario first."));

        PolicyPreset preset = null;
        Weights w;
        boolean fullCoverage = false;
        Integer floor = null;
        String policyName, policyKey, policyVersion;

        if (req != null && req.policyKey() != null && !req.policyKey().isBlank()) {
            preset = presets.findByKey(req.policyKey())
                    .orElseThrow(() -> ApiException.badRequest("Unknown policy selection."));
            w = Weights.parse(preset.weightsJson, json);
            fullCoverage = preset.fullCoverage;
            floor = req.fairnessFloorPct() != null ? req.fairnessFloorPct() : preset.fairnessFloorPct;
            policyName = preset.name;
            policyKey = preset.key;
            policyVersion = preset.version;
        } else if (req != null && req.weights() != null && !req.weights().isEmpty()) {
            w = toWeights(req.weights());
            fullCoverage = Boolean.TRUE.equals(req.fullCoverage());
            floor = req.fairnessFloorPct();
            policyName = "Custom Policy";
            policyKey = "custom";
            policyVersion = "v1";
        } else {
            preset = presets.findByKey("balanced").orElse(null);
            w = preset != null ? Weights.parse(preset.weightsJson, json) : Weights.defaultBalanced();
            fullCoverage = preset != null && preset.fullCoverage;
            floor = preset != null ? preset.fairnessFloorPct : null;
            policyName = preset != null ? preset.name : "Balanced Allocation Policy";
            policyKey = preset != null ? preset.key : "balanced";
            policyVersion = preset != null ? preset.version : "v1";
        }
        if (floor != null && (floor < 0 || floor > 95)) {
            throw ApiException.badRequest("The fairness floor must be between 0% and 95%.");
        }

        AllocationRun run = new AllocationRun();
        run.runCode = "RUN-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        run.number = nextRunNumber();
        run.scenario = snap.scenario;
        run.snapshotId = snap.id;
        run.policyName = policyName;
        run.policyKey = policyKey;
        run.policyVersion = policyVersion;
        run.weightsJson = w.toJson(json);
        run.fullCoverage = fullCoverage;
        run.fairnessFloorPct = floor;
        run.parentRunId = parentRunId;
        run.overridesJson = overridesJson;
        AllocationRun saved = runs.save(run);

        audit.log(actor, AuditAction.ALLOCATION_STARTED, "AllocationRun", saved.runCode,
                "Allocation run #" + saved.number + " started for " + Labels.label(saved.scenario)
                        + " using " + policyName + ".", null);
        if (preset != null && !"balanced".equals(preset.key)) {
            audit.log(actor, AuditAction.POLICY_UPDATED, "Policy", policyKey,
                    "Allocation run #" + saved.number + " is using " + policyName + ".", null);
        }
        return saved;
    }

    /** Submits an already-persisted run to the execution pipeline (used by reallocation). */
    public void submitForExecution(AllocationRun run) {
        if (!runActive.compareAndSet(false, true)) {
            throw ApiException.conflict("An allocation is already in progress. Please wait for it to complete.");
        }
        try {
            executor.submit(() -> execute(run.id));
        } catch (RuntimeException e) {
            runActive.set(false);
            throw e;
        }
    }

    /** Admin cancel: stops a queued/in-progress run. */
    public AllocationRun cancelRun(Long id, AuthUser actor) {
        AllocationRun run = requireRun(id);
        if (run.status != RunStatus.QUEUED && run.status != RunStatus.RUNNING) {
            throw ApiException.badRequest("Only queued or in-progress runs can be cancelled.");
        }
        run.status = RunStatus.FAILED;
        run.errorMessage = "Cancelled by the administrator.";
        run.completedAt = Instant.now();
        runs.save(run);
        runActive.set(false);
        audit.log(actor, AuditAction.ADMIN_ACTION, "Allocation run", String.valueOf(run.number),
                "Run #" + run.number + " cancelled by administrator.", java.util.Map.of());
        return run;
    }

    public AllocationRun requireRun(Long id) {
        return runs.findById(id).orElseThrow(() -> ApiException.notFound("Allocation run not found."));
    }

    public int nextRunNumber() {
        return runs.findTopByOrderByIdDesc().map(r -> r.number + 1).orElse(1);
    }

    private Weights toWeights(Map<String, Double> m) {
        Weights w = new Weights(
                m.getOrDefault("skills", 0.0), m.getOrDefault("qualification", 0.0),
                m.getOrDefault("interest", 0.0), m.getOrDefault("location", 0.0),
                m.getOrDefault("preference", 0.0), m.getOrDefault("learning", 0.0),
                m.getOrDefault("experience", 0.0));
        if (Math.abs(w.sum() - 100) > 0.001) {
            throw ApiException.badRequest("Policy weights must total exactly 100%.");
        }
        for (String k : Weights.KEYS) {
            double v = m.getOrDefault(k, 0.0);
            if (v < 0 || v > 100) throw ApiException.badRequest("Each weight must be between 0 and 100.");
        }
        return w;
    }

    // ------------------------------------------------------------------
    // Execution pipeline
    // ------------------------------------------------------------------

    private void execute(Long runId) {
        AllocationRun run = runs.findById(runId).orElse(null);
        if (run == null) {
            runActive.set(false);
            return;
        }
        long t0 = System.currentTimeMillis();
        StageTracker st = new StageTracker(json);
        try {
            run.status = RunStatus.RUNNING;
            run.startedAt = Instant.now();
            runs.save(run);
            st.mark("Checking data");

            List<CandidateProfile> candidates = profiles.findAllByCandidateState(CandidateState.ACTIVE);
            List<Long> candIds = candidates.stream().map(p -> p.id).toList();
            Map<Long, Set<String>> skillsMap = new HashMap<>();
            for (CandidateSkill s : skills.findByCandidateIdIn(candIds)) {
                if (s.validated) skillsMap.computeIfAbsent(s.candidateId, k -> new HashSet<>()).add(s.canonical);
            }
            Map<Long, Set<Sector>> interestMap = new HashMap<>();
            for (CandidateInterest i : interests.findByCandidateIdIn(candIds)) {
                interestMap.computeIfAbsent(i.candidateId, k -> new HashSet<>()).add(i.sector);
            }
            Map<Long, Map<Long, Integer>> prefMap = new HashMap<>();
            for (CandidatePreference p : preferences.findByCandidateIdIn(candIds)) {
                prefMap.computeIfAbsent(p.candidateId, k -> new HashMap<>()).put(p.opportunityId, p.rank);
            }

            // Operational overrides from reallocation (sandbox: never mutates production data).
            Map<Long, Integer> capacityOverrides = new HashMap<>();
            Set<Long> withdrawnCandidates = new HashSet<>();
            Set<Long> withdrawnOpportunities = new HashSet<>();
            parseOverrides(run, capacityOverrides, withdrawnCandidates, withdrawnOpportunities);
            candidates = candidates.stream().filter(p -> !withdrawnCandidates.contains(p.id)).toList();
            List<Opportunity> opps = opportunities.findAllByStatus(OppStatus.ACTIVE).stream()
                    .filter(o -> !withdrawnOpportunities.contains(o.id)
                            && capacityOverrides.getOrDefault(o.id, o.capacity) > 0)
                    .toList();
            List<Long> oppIds = opps.stream().map(o -> o.id).toList();
            Map<Long, List<OpportunitySkill>> reqMap = new HashMap<>();
            for (OpportunitySkill s : oppSkills.findByOpportunityIdIn(oppIds)) {
                reqMap.computeIfAbsent(s.opportunityId, k -> new ArrayList<>()).add(s);
            }

            Weights w = Weights.parse(run.weightsJson, json);
            st.mark("Evaluating eligibility");
            List<EligiblePair> pairs = new ArrayList<>();
            Map<Long, Integer> eligiblePerOpp = new HashMap<>();
            for (CandidateProfile p : candidates) {
                Set<String> sk = skillsMap.getOrDefault(p.id, Set.of());
                for (Opportunity o : opps) {
                    List<OpportunitySkill> req = reqMap.getOrDefault(o.id, List.of());
                    if (!suitability.check(p, sk, o, req).eligible()) continue;
                    Integer rank = prefMap.getOrDefault(p.id, Map.of()).get(o.id);
                    PairScore s = suitability.score(p, sk, interestMap.getOrDefault(p.id, Set.of()),
                            rank, o, req, w);
                    pairs.add(new EligiblePair(p.id, o.id, s, rank));
                    eligiblePerOpp.merge(o.id, 1, Integer::sum);
                }
            }
            st.mark("Calculating suitability");

            st.mark("Finding the best allocation");
            AiServiceClient.OptimizeRequest request = buildOptimizerRequest(run, candidates, opps,
                    pairs, eligiblePerOpp, capacityOverrides);
            AiServiceClient.OptimizeResult result = ai.optimize(request);
            run.eligiblePairs = pairs.size();
            if (result.model() != null) {
                run.variables = result.model().variables();
                run.constraints = result.model().constraints();
            }
            run.solverRuntimeMs = result.runtimeMs();

            if ("INFEASIBLE".equalsIgnoreCase(result.status())) {
                run.solverStatus = SolverStatus.INFEASIBLE;
                run.status = RunStatus.INFEASIBLE;
                run.errorMessage = infeasibilityMessage(run, candidates.size(),
                        opps.stream().mapToInt(o -> capacityOverrides.getOrDefault(o.id, o.capacity)).sum());
                st.mark("Finalizing results");
                finishRun(run, st, t0);
                audit.logSystem(AuditAction.ALLOCATION_INFEASIBLE, "AllocationRun", run.runCode,
                        "Allocation run #" + run.number + " is infeasible: " + run.errorMessage,
                        Map.of("eligiblePairs", run.eligiblePairs, "scenario", run.scenario.name()));
                return;
            }

            run.solverStatus = "OPTIMAL".equalsIgnoreCase(result.status())
                    ? SolverStatus.OPTIMAL : SolverStatus.FEASIBLE;
            run.objective = result.objective();
            st.mark("Evaluating fairness");

            Map<Long, Map<Long, EligiblePair>> pairIndex = indexPairs(pairs);
            st.mark("Finalizing results");
            persistAssignments(run, result.global(), pairIndex, AssignmentSource.GLOBAL, eligiblePerOpp);
            persistAssignments(run, result.greedy(), pairIndex, AssignmentSource.BASELINE, eligiblePerOpp);
            List<Assignment> global = assignments.findByRunIdAndSource(run.id, AssignmentSource.GLOBAL);
            metrics.persist(run, global, candidates, opps, prefMap, eligiblePerOpp, capacityOverrides);

            run.status = RunStatus.COMPLETED;
            run.completedAt = Instant.now();
            finishRun(run, st, t0);
            long allocated = global.size();
            int seats = opps.stream().mapToInt(o -> capacityOverrides.getOrDefault(o.id, o.capacity)).sum();
            double utilization = seats > 0 ? Math.round(allocated * 1000.0 / seats) / 10.0 : 0;
            double avgSuitability = global.isEmpty() ? 0
                    : Math.round(global.stream().mapToDouble(a -> a.suitability).average().orElse(0) * 10.0) / 10.0;
            audit.logSystem(AuditAction.ALLOCATION_COMPLETED, "AllocationRun", run.runCode,
                    "Allocation run #" + run.number + " completed: " + candidates.size()
                            + " candidates evaluated, " + allocated + " allocated, "
                            + utilization + "% of seats filled.",
                    Map.of("allocated", allocated, "unallocated", candidates.size() - allocated,
                            "seatUtilization", utilization, "avgSuitability", avgSuitability,
                            "solverStatus", run.solverStatus.name(),
                            "solverRuntimeMs", run.solverRuntimeMs));
            if (run.parentRunId != null) {
                audit.logSystem(AuditAction.REALLOCATION_COMPLETED, "AllocationRun", run.runCode,
                        "Reallocation run #" + run.number + " completed (parent run #" + run.parentRunId + ").",
                        null);
            }
        } catch (Exception e) {
            log.error("Allocation run {} failed", runId, e);
            run.status = RunStatus.FAILED;
            run.errorMessage = "The allocation could not be completed. Please try again.";
            run.completedAt = Instant.now();
            finishRun(run, st, t0);
            audit.logSystem(AuditAction.ALLOCATION_FAILED, "AllocationRun", run.runCode,
                    "Allocation run #" + run.number + " failed.",
                    Map.of("technical", String.valueOf(e).substring(0, Math.min(200, String.valueOf(e).length()))));
        } finally {
            runActive.set(false);
        }
    }

    private void finishRun(AllocationRun run, StageTracker st, long t0) {
        run.totalRuntimeMs = System.currentTimeMillis() - t0;
        run.stageTimingsJson = st.json();
        runs.save(run);
    }

    private AiServiceClient.OptimizeRequest buildOptimizerRequest(AllocationRun run,
                                                                  List<CandidateProfile> candidates,
                                                                  List<Opportunity> opps,
                                                                  List<EligiblePair> pairs,
                                                                  Map<Long, Integer> eligiblePerOpp,
                                                                  Map<Long, Integer> capacityOverrides) {
        Set<Integer> candIds = new HashSet<>();
        List<AiServiceClient.OptPair> pairList = new ArrayList<>(pairs.size());
        for (EligiblePair p : pairs) {
            int c = p.candidateId().intValue();
            int i = p.opportunityId().intValue();
            candIds.add(c);
            pairList.add(new AiServiceClient.OptPair(c, i,
                    (int) Math.round(p.score().overall() * 100),
                    p.preferenceRank() != null ? 1 : 0));
        }
        List<Integer> candidateList = candIds.stream().sorted().toList();
        List<AiServiceClient.OptOpp> oppList = opps.stream()
                .map(o -> new AiServiceClient.OptOpp(o.id.intValue(),
                        capacityOverrides.getOrDefault(o.id, o.capacity)))
                .toList();
        List<AiServiceClient.OptGroup> groups = new ArrayList<>();
        if (run.fairnessFloorPct != null && run.fairnessFloorPct > 0) {
            for (LocationType lt : LocationType.values()) {
                List<Integer> members = candidates.stream()
                        .filter(p -> p.locationType == lt)
                        .map(p -> p.id.intValue()).sorted().toList();
                if (!members.isEmpty()) {
                    groups.add(new AiServiceClient.OptGroup(lt.name(), members, run.fairnessFloorPct));
                }
            }
        }
        return new AiServiceClient.OptimizeRequest(candidateList, oppList, pairList,
                new AiServiceClient.OptPolicy(run.fullCoverage, groups),
                new AiServiceClient.OptParams(props.getOptimizer().getMaxWallSeconds(), 1, 0L));
    }

    private Map<Long, Map<Long, EligiblePair>> indexPairs(List<EligiblePair> pairs) {
        Map<Long, Map<Long, EligiblePair>> index = new HashMap<>();
        for (EligiblePair p : pairs) {
            index.computeIfAbsent(p.candidateId(), k -> new HashMap<>())
                    .put(p.opportunityId(), p);
        }
        return index;
    }

    private void persistAssignments(AllocationRun run, List<AiServiceClient.OptAssignment> list,
                                    Map<Long, Map<Long, EligiblePair>> pairIndex,
                                    AssignmentSource source, Map<Long, Integer> eligiblePerOpp) {
        if (list == null) return;
        List<Assignment> batch = new ArrayList<>(list.size());
        for (AiServiceClient.OptAssignment a : list) {
            Map<Long, EligiblePair> byOpp = pairIndex.get(Long.valueOf(a.c()));
            if (byOpp == null) continue;
            EligiblePair ep = byOpp.get(Long.valueOf(a.i()));
            if (ep == null) continue;
            Assignment asg = new Assignment();
            asg.runId = run.id;
            asg.candidateId = ep.candidateId();
            asg.opportunityId = ep.opportunityId();
            asg.source = source;
            asg.suitability = ep.score().overall();
            asg.skillsFit = ep.score().skills();
            asg.qualificationFit = ep.score().qualification();
            asg.interestFit = ep.score().interest();
            asg.locationFit = ep.score().location();
            asg.preferenceFit = ep.score().preference();
            asg.learningFit = ep.score().learning();
            asg.experienceFit = ep.score().experience();
            asg.breakdownJson = breakdownJson(run, ep.score());
            asg.reason = buildReason(source, ep, eligiblePerOpp);
            batch.add(asg);
        }
        assignments.saveAll(batch);
    }

    private String buildReason(AssignmentSource source, EligiblePair ep, Map<Long, Integer> eligiblePerOpp) {
        int eligible = eligiblePerOpp.getOrDefault(ep.opportunityId(), 0);
        if (source == AssignmentSource.BASELINE) {
            return "Sequential baseline reference: the best opportunity still available when this candidate was processed.";
        }
        StringBuilder sb = new StringBuilder("Selected by the global allocation. ");
        sb.append(eligible).append(" eligible candidates competed for this opportunity's seats.");
        if (ep.preferenceRank() != null) {
            sb.append(" It was this candidate's ").append(ep.preferenceRank()).append(" stated preference.");
        }
        return sb.toString();
    }

    private String breakdownJson(AllocationRun run, PairScore s) {
        try {
            Weights w = Weights.parse(run.weightsJson, json);
            Map<String, Object> b = new LinkedHashMap<>();
            putContribution(b, "skills", s.skills(), w.skills());
            putContribution(b, "qualification", s.qualification(), w.qualification());
            putContribution(b, "interest", s.interest(), w.interest());
            putContribution(b, "location", s.location(), w.location());
            putContribution(b, "preference", s.preference(), w.preference());
            putContribution(b, "learning", s.learning(), w.learning());
            putContribution(b, "experience", s.experience(), w.experience());
            b.put("overall", s.overall());
            return json.writeValueAsString(b);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void putContribution(Map<String, Object> b, String key, double fit, double weight) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fit", fit);
        item.put("weight", weight);
        item.put("contribution", Math.round(fit * weight / 100.0 * 10.0) / 10.0);
        b.put(key, item);
    }

    private void parseOverrides(AllocationRun run, Map<Long, Integer> capacityOverrides,
                                Set<Long> withdrawnCandidates, Set<Long> withdrawnOpportunities) {
        if (run.overridesJson == null || run.overridesJson.isBlank()) return;
        try {
            JsonNode n = json.readTree(run.overridesJson);
            JsonNode caps = n.path("capacityOverrides");
            caps.fields().forEachRemaining(e -> capacityOverrides.put(Long.valueOf(e.getKey()), e.getValue().asInt()));
            n.path("withdrawnCandidates").forEach(v -> withdrawnCandidates.add(v.asLong()));
            n.path("withdrawnOpportunities").forEach(v -> withdrawnOpportunities.add(v.asLong()));
        } catch (Exception e) {
            log.warn("Cannot parse overrides for run {}", run.id, e);
        }
    }

    private String infeasibilityMessage(AllocationRun run, int candidateCount, int seatCount) {
        StringBuilder sb = new StringBuilder(
                "This configuration cannot be satisfied with the current eligible candidates and available opportunity capacity.");
        if (run.fullCoverage) {
            sb.append(" The rule requires all ").append(candidateCount)
                    .append(" candidates to be allocated, but only ").append(seatCount)
                    .append(" seats exist.");
        }
        if (run.fairnessFloorPct != null && run.fairnessFloorPct > 0) {
            sb.append(" The fairness floor of ").append(run.fairnessFloorPct)
                    .append("% exceeds what the eligible candidates and seat capacity allow.");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Stage timing tracker (actual backend timings, never simulated)
    // ------------------------------------------------------------------

    private static final class StageTracker {
        private final ObjectMapper json;
        private final List<Map<String, Object>> stages = new ArrayList<>();
        private long last = System.currentTimeMillis();

        StageTracker(ObjectMapper json) { this.json = json; }

        void mark(String stage) {
            long now = System.currentTimeMillis();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stage", stage);
            m.put("at", Instant.ofEpochMilli(now).toString());
            m.put("ms", now - last);
            stages.add(m);
            last = now;
        }

        String json() {
            try {
                return json.writeValueAsString(stages);
            } catch (Exception e) {
                return "[]";
            }
        }
    }
}

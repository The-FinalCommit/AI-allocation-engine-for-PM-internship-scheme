package in.pragati.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.pragati.ai.AiServiceClient;
import in.pragati.common.ApiException;
import in.pragati.common.Weights;
import in.pragati.domain.*;
import in.pragati.domain.enums.*;
import in.pragati.repo.*;
import in.pragati.security.AuthUser;
import in.pragati.service.SuitabilityService.EligiblePair;
import in.pragati.service.SuitabilityService.PairScore;

/**
 * What-if policy simulation. Runs the real optimizer in a sandbox against
 * the same dataset snapshot as the selected completed run; never mutates
 * the production allocation or the dataset.
 */
@Service
public class PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyService.class);

    public record SimulateRequest(Long baseRunId, Map<String, Double> weights,
                                  Boolean fullCoverage, Integer fairnessFloorPct) { }

    private final AllocationRunRepository runs;
    private final DatasetSnapshotRepository snapshots;
    private final CandidateProfileRepository profiles;
    private final CandidateSkillRepository skills;
    private final CandidateInterestRepository interests;
    private final CandidatePreferenceRepository preferences;
    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository oppSkills;
    private final AssignmentRepository assignments;
    private final SimulationRunRepository sims;
    private final PolicyPresetRepository presets;
    private final AiServiceClient ai;
    private final SuitabilityService suitability;
    private final AuditService audit;
    private final ObjectMapper json;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pragati-simulation");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean simActive = new AtomicBoolean(false);

    public PolicyService(AllocationRunRepository runs, DatasetSnapshotRepository snapshots,
                         CandidateProfileRepository profiles, CandidateSkillRepository skills,
                         CandidateInterestRepository interests, CandidatePreferenceRepository preferences,
                         OpportunityRepository opportunities, OpportunitySkillRepository oppSkills,
                         AssignmentRepository assignments, SimulationRunRepository sims,
                         PolicyPresetRepository presets, AiServiceClient ai,
                         SuitabilityService suitability, AuditService audit, ObjectMapper json) {
        this.runs = runs; this.snapshots = snapshots; this.profiles = profiles;
        this.skills = skills; this.interests = interests; this.preferences = preferences;
        this.opportunities = opportunities; this.oppSkills = oppSkills;
        this.assignments = assignments; this.sims = sims; this.presets = presets;
        this.ai = ai; this.suitability = suitability; this.audit = audit; this.json = json;
    }

    public boolean isSimulationActive() { return simActive.get(); }

    public List<PolicyPreset> presets() {
        return presets.findAll();
    }

    public SimulationRun startSimulation(SimulateRequest req, AuthUser actor) {
        if (!simActive.compareAndSet(false, true)) {
            throw ApiException.conflict("A simulation is already in progress. Please wait for it to complete.");
        }
        if (req.baseRunId() == null) {
            runActiveOff();
            throw ApiException.badRequest("Please select the allocation run to simulate against.");
        }
        AllocationRun base = runs.findById(req.baseRunId())
                .orElseThrow(() -> {
                    runActiveOff();
                    return ApiException.notFound("The selected allocation run was not found.");
                });
        if (base.status != RunStatus.COMPLETED) {
            runActiveOff();
            throw ApiException.badRequest("Simulations are available for completed allocation runs only.");
        }
        DatasetSnapshot current = snapshots.findTopByOrderByIdDesc().orElseThrow();
        if (!current.id.equals(base.snapshotId)) {
            runActiveOff();
            throw ApiException.conflict("The dataset changed after this run completed. Run a fresh allocation first, then simulate.");
        }

        Weights w;
        String policyName;
        if (req.weights() != null && !req.weights().isEmpty()) {
            w = new Weights(
                    req.weights().getOrDefault("skills", 0.0), req.weights().getOrDefault("qualification", 0.0),
                    req.weights().getOrDefault("interest", 0.0), req.weights().getOrDefault("location", 0.0),
                    req.weights().getOrDefault("preference", 0.0), req.weights().getOrDefault("learning", 0.0),
                    req.weights().getOrDefault("experience", 0.0));
            if (Math.abs(w.sum() - 100) > 0.001) {
                runActiveOff();
                throw ApiException.badRequest("Policy weights must total exactly 100%.");
            }
            policyName = "Custom Simulation Policy";
        } else {
            w = Weights.parse(base.weightsJson, json);
            policyName = "Current Policy (unchanged)";
        }
        boolean fullCoverage = Boolean.TRUE.equals(req.fullCoverage());
        Integer floor = req.fairnessFloorPct();
        if (floor != null && (floor < 0 || floor > 95)) {
            runActiveOff();
            throw ApiException.badRequest("The fairness floor must be between 0% and 95%.");
        }

        SimulationRun sim = new SimulationRun();
        sim.baseRunId = base.id;
        sim.policyName = policyName;
        sim.weightsJson = w.toJson(json);
        sim.fullCoverage = fullCoverage;
        sim.fairnessFloorPct = floor;
        sim.status = RunStatus.RUNNING;
        SimulationRun saved = sims.save(sim);
        executor.submit(() -> runSimulation(saved.id, base, w, fullCoverage, floor, actor));
        return saved;
    }

    private void runActiveOff() { simActive.set(false); }

    private void runSimulation(Long simId, AllocationRun base, Weights w,
                               boolean fullCoverage, Integer floor, AuthUser actor) {
        long t0 = System.currentTimeMillis();
        SimulationRun sim = sims.findById(simId).orElseThrow();
        try {
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
            List<Opportunity> opps = opportunities.findAllByStatus(OppStatus.ACTIVE);
            List<Long> oppIds = opps.stream().map(o -> o.id).toList();
            Map<Long, List<OpportunitySkill>> reqMap = new HashMap<>();
            for (OpportunitySkill s : oppSkills.findByOpportunityIdIn(oppIds)) {
                reqMap.computeIfAbsent(s.opportunityId, k -> new ArrayList<>()).add(s);
            }

            List<EligiblePair> pairs = new ArrayList<>();
            Map<Long, Integer> eligiblePerOpp = new HashMap<>();
            for (CandidateProfile p : candidates) {
                Set<String> sk = skillsMap.getOrDefault(p.id, Set.of());
                for (Opportunity o : opps) {
                    List<OpportunitySkill> req = reqMap.getOrDefault(o.id, List.of());
                    if (!suitability.check(p, sk, o, req).eligible()) continue;
                    Integer rank = prefMap.getOrDefault(p.id, Map.of()).get(o.id);
                    PairScore s = suitability.score(p, sk, interestMap.getOrDefault(p.id, Set.of()), rank, o, req, w);
                    pairs.add(new EligiblePair(p.id, o.id, s, rank));
                    eligiblePerOpp.merge(o.id, 1, Integer::sum);
                }
            }

            Set<Integer> candListSet = new HashSet<>();
            List<AiServiceClient.OptPair> pairList = new ArrayList<>();
            for (EligiblePair p : pairs) {
                candListSet.add(p.candidateId().intValue());
                pairList.add(new AiServiceClient.OptPair(p.candidateId().intValue(),
                        p.opportunityId().intValue(), (int) Math.round(p.score().overall() * 100),
                        p.preferenceRank() != null ? 1 : 0));
            }
            List<AiServiceClient.OptOpp> oppList = opps.stream()
                    .map(o -> new AiServiceClient.OptOpp(o.id.intValue(), o.capacity)).toList();
            List<AiServiceClient.OptGroup> groups = new ArrayList<>();
            if (floor != null && floor > 0) {
                for (LocationType lt : LocationType.values()) {
                    List<Integer> members = candidates.stream().filter(p -> p.locationType == lt)
                            .map(p -> p.id.intValue()).sorted().toList();
                    if (!members.isEmpty()) groups.add(new AiServiceClient.OptGroup(lt.name(), members, floor));
                }
            }
            AiServiceClient.OptimizeResult result = ai.optimize(new AiServiceClient.OptimizeRequest(
                    candListSet.stream().sorted().toList(), oppList, pairList,
                    new AiServiceClient.OptPolicy(fullCoverage, groups),
                    new AiServiceClient.OptParams(90, 1, 0L)));

            sim.solverStatus = "INFEASIBLE".equalsIgnoreCase(result.status())
                    ? SolverStatus.INFEASIBLE
                    : ("OPTIMAL".equalsIgnoreCase(result.status()) ? SolverStatus.OPTIMAL : SolverStatus.FEASIBLE);
            if (sim.solverStatus == SolverStatus.INFEASIBLE) {
                sim.status = RunStatus.INFEASIBLE;
                sim.metricsJson = json.writeValueAsString(Map.of(
                        "note", "This configuration cannot be satisfied with the current eligible candidates and available opportunity capacity."));
            } else {
                sim.status = RunStatus.COMPLETED;
                Map<String, Object> metrics = computeMetrics(base, candidates, opps, prefMap, result);
                sim.metricsJson = json.writeValueAsString(metrics);
                sim.affectedCount = ((Number) metrics.getOrDefault("affected", 0)).intValue();
                sim.stability = ((Number) metrics.getOrDefault("stability", 0.0)).doubleValue();
            }
            sim.runtimeMs = System.currentTimeMillis() - t0;
            sims.save(sim);
            audit.log(actor, AuditAction.SIMULATION_COMPLETED, "SimulationRun", String.valueOf(sim.id),
                    "Policy simulation " + (sim.status == RunStatus.INFEASIBLE ? "was infeasible" : "completed")
                            + " (" + sim.policyName + ").", Map.of("baseRun", base.number));
        } catch (Exception e) {
            log.error("Simulation {} failed", simId, e);
            sim.status = RunStatus.FAILED;
            sim.runtimeMs = System.currentTimeMillis() - t0;
            sims.save(sim);
        } finally {
            simActive.set(false);
        }
    }

    private Map<String, Object> computeMetrics(AllocationRun base, List<CandidateProfile> candidates,
                                               List<Opportunity> opps,
                                               Map<Long, Map<Long, Integer>> prefMap,
                                               AiServiceClient.OptimizeResult result) {
        List<Assignment> baseGlobal = assignments.findByRunIdAndSource(base.id, AssignmentSource.GLOBAL);
        Map<Long, Assignment> baseByCand = new HashMap<>();
        for (Assignment a : baseGlobal) baseByCand.put(a.candidateId, a);
        int totalSeats = opps.stream().mapToInt(o -> o.capacity).sum();

        Map<String, Object> current = runMetrics(baseGlobal, baseByCand, prefMap, candidates.size(), totalSeats);

        Map<Long, Long> newByCand = new HashMap<>();
        for (AiServiceClient.OptAssignment a : result.global()) {
            newByCand.put(Long.valueOf(a.c()), Long.valueOf(a.i()));
        }
        int affected = 0;
        int unchanged = 0;
        double sumSuit = 0;
        int prefSat = 0;
        for (Map.Entry<Long, Long> e : newByCand.entrySet()) {
            Assignment prev = baseByCand.get(e.getKey());
            if (prev == null || !prev.opportunityId.equals(e.getValue())) affected++;
            else unchanged++;
            long score = result.global().stream()
                    .filter(a -> a.c() == e.getKey().intValue())
                    .mapToLong(a -> a.score()).findFirst().orElse(0);
            sumSuit += score / 100.0;
            Map<Long, Integer> prefs = prefMap.getOrDefault(e.getKey(), Map.of());
            if (prefs.containsKey(e.getValue())) prefSat++;
        }
        // Candidates who were allocated before but are not allocated anymore are affected too.
        for (Long cand : baseByCand.keySet()) {
            if (!newByCand.containsKey(cand)) affected++;
        }
        int newAllocated = newByCand.size();
        Map<String, Object> scenario = new java.util.LinkedHashMap<>();
        scenario.put("allocated", newAllocated);
        scenario.put("unallocated", candidates.size() - newAllocated);
        scenario.put("suitability", newAllocated > 0 ? r1(sumSuit / newAllocated) : 0);
        scenario.put("preferenceSatisfaction", newAllocated > 0 ? r1(prefSat * 100.0 / newAllocated) : 0);
        scenario.put("seatUtilization", totalSeats > 0 ? r1(newAllocated * 100.0 / totalSeats) : 0);

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("current", current);
        out.put("scenario", scenario);
        out.put("affected", affected);
        out.put("stability", baseGlobal.isEmpty() ? 100.0 : r1((double) unchanged / baseGlobal.size() * 100));
        out.put("objective", result.objective());
        return out;
    }

    private Map<String, Object> runMetrics(List<Assignment> list, Map<Long, Assignment> byCand,
                                           Map<Long, Map<Long, Integer>> prefMap,
                                           int candidateCount, int totalSeats) {
        double sumSuit = list.stream().mapToDouble(a -> a.suitability).sum();
        int prefSat = 0;
        for (Assignment a : list) {
            if (prefMap.getOrDefault(a.candidateId, Map.of()).containsKey(a.opportunityId)) prefSat++;
        }
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("allocated", list.size());
        m.put("unallocated", candidateCount - list.size());
        m.put("suitability", list.isEmpty() ? 0 : r1(sumSuit / list.size()));
        m.put("preferenceSatisfaction", list.isEmpty() ? 0 : r1(prefSat * 100.0 / list.size()));
        m.put("seatUtilization", totalSeats > 0 ? r1(list.size() * 100.0 / totalSeats) : 0);
        return m;
    }

    private double r1(double v) { return Math.round(v * 10.0) / 10.0; }
}

package in.pragati.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import in.pragati.common.ApiException;
import in.pragati.common.Labels;
import in.pragati.common.Weights;
import in.pragati.domain.*;
import in.pragati.domain.enums.*;
import in.pragati.repo.*;
import in.pragati.security.AuthUser;
import in.pragati.service.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Command center, allocation runs, policy, fairness, geography, reallocation, audit.")
public class AdminController {

    // ------------------------------------------------------------------
    // DTOs
    // ------------------------------------------------------------------

    public record ScenarioSummary(String key, String name, String description, boolean active,
                                  java.time.Instant lastLoadedAt) { }
    public record DatasetSummary(String key, String name, int candidateCount, int opportunityCount,
                                 int seatCount, int version, String fingerprint, boolean synthetic) { }
    public record RunSummary(Long id, int number, String code, String scenario, String policyName,
                             String status, String solverStatus, java.time.Instant createdAt,
                             java.time.Instant completedAt, Long totalRuntimeMs, boolean stale,
                             boolean reallocation, Integer parentRunNumber) { }
    public record RunDetail(RunSummary run, DatasetSummary dataset, String infeasibleMessage,
                            List<OverviewService.StageInfo> stages, Provenance provenance) { }
    public record Provenance(String runId, int runNumber, String scenario, String datasetVersion,
                             String datasetFingerprint, int candidates, int opportunities, int seats,
                             long seed, String policyName, String policyKey, String policyVersion,
                             Map<String, Double> weights, String optimizer, String solverStatus,
                             Long objective, int variables, int constraints, long solverRuntimeMs,
                             long totalRuntimeMs, int workers, long solverSeed) { }
    public record GroupRow(String groupValue, int population, int allocated, double rate,
                           double avgSuitability, double preferenceSatisfaction) { }
    public record FairnessDto(List<GroupRow> ruralUrban, List<GroupRow> states, List<GroupRow> qualifications,
                              Disparity disparity, String note) { }
    public record Disparity(String higherGroup, String lowerGroup, double gapPercentagePoints, String explanation) { }
    public record GeoState(String state, int demand, int capacity, int allocated, int unmetDemand,
                           double pressure, double allocationRate) { }
    public record GeoTotals(int demand, int capacity, int allocated, int unmetDemand) { }
    public record GeoDto(List<GeoState> states, GeoTotals totals) { }
    public record ConflictRow(Long opportunityId, String title, int eligible, int seats,
                              int allocated, int unmetDemand, double pressure) { }
    public record AuditRow(Long id, String at, String action, String actor, String summary,
                           Map<String, Object> details) { }
    public record StartRunRequest(String policyKey, Map<String, Double> weights,
                                  Boolean fullCoverage, Integer fairnessFloorPct) { }

    // ------------------------------------------------------------------
    // Controller
    // ------------------------------------------------------------------

    private final OverviewService overview;
    private final ScenarioService scenarios;
    private final AllocationService allocation;
    private final ComparisonService comparison;
    private final PolicyService policy;
    private final ReallocationService reallocation;
    private final DataQualityService dataQuality;
    private final AuditService audit;
    private final JudgeService judge;
    private final TechnicalService technical;
    private final PdfReportService report;
    private final DatasetSnapshotRepository snapshots;
    private final AllocationRunRepository runs;
    private final AssignmentRepository assignments;
    private final GroupMetricRepository groupMetrics;
    private final GeoMetricRepository geoMetrics;
    private final ConflictMetricRepository conflicts;
    private final OpportunityRepository opportunities;
    private final AuditEventRepository auditEvents;
    private final SimulationRunRepository simulations;
    private final ObjectMapper json;

    public AdminController(OverviewService overview, ScenarioService scenarios,
                           AllocationService allocation, ComparisonService comparison,
                           PolicyService policy, ReallocationService reallocation,
                           DataQualityService dataQuality, AuditService audit, JudgeService judge,
                           TechnicalService technical, PdfReportService report,
                           DatasetSnapshotRepository snapshots, AllocationRunRepository runs,
                           AssignmentRepository assignments, GroupMetricRepository groupMetrics,
                           GeoMetricRepository geoMetrics, ConflictMetricRepository conflicts,
                           OpportunityRepository opportunities, AuditEventRepository auditEvents,
                           SimulationRunRepository simulations, ObjectMapper json) {
        this.overview = overview; this.scenarios = scenarios; this.allocation = allocation;
        this.comparison = comparison; this.policy = policy; this.reallocation = reallocation;
        this.dataQuality = dataQuality; this.audit = audit; this.judge = judge;
        this.technical = technical; this.report = report; this.snapshots = snapshots;
        this.runs = runs; this.assignments = assignments; this.groupMetrics = groupMetrics;
        this.geoMetrics = geoMetrics; this.conflicts = conflicts; this.opportunities = opportunities;
        this.auditEvents = auditEvents; this.simulations = simulations; this.json = json;
    }

    // ---- Command center ----

    @GetMapping("/overview")
    @Operation(summary = "Current state of the allocation system (Command Center).")
    public OverviewService.Overview overview(@AuthenticationPrincipal AuthUser me) {
        return overview.overview();
    }

    // ---- Scenarios ----

    @GetMapping("/scenarios")
    @Operation(summary = "Available demonstration scenarios.")
    public List<ScenarioSummary> scenarios() {
        DatasetSnapshot current = snapshots.findTopByOrderByIdDesc().orElse(null);
        java.time.Instant loadedAt = current != null ? current.createdAt : null;
        return List.of(
                scenario("STANDARD_SHOWCASE", "Standard Showcase",
                        "Balanced showcase across sectors and states with real seat scarcity and a few hot, competitive internships.",
                        current, loadedAt),
                scenario("MICRO_CONFLICT", "Micro Conflict Proof",
                        "A small engineered conflict showing why global allocation beats candidate-by-candidate processing.",
                        current, loadedAt),
                scenario("HIGH_CONFLICT", "High Competition",
                        "Scarce seats in hot sectors — far more qualified candidates than there are positions.",
                        current, loadedAt),
                scenario("GEOGRAPHIC", "Geographic Impact",
                        "Opportunities concentrated in six states — location materially shapes outcomes.",
                        current, loadedAt),
                scenario("FAIRNESS", "Fairness Study",
                        "Meaningful rural and urban differences for group-level outcome measurement.",
                        current, loadedAt),
                scenario("INFEASIBLE", "Infeasibility Demo",
                        "Demand far exceeds seats — pair with the Full Coverage policy to see a real infeasible result.",
                        current, loadedAt),
                scenario("BENCHMARK", "Performance Benchmark",
                        "A large field for measured performance evidence — the engine should stay fast under real scale.",
                        current, loadedAt));
    }

    private ScenarioSummary scenario(String key, String name, String description,
                                     DatasetSnapshot current, java.time.Instant loadedAt) {
        boolean active = current != null && current.scenario.name().equals(key);
        return new ScenarioSummary(key, name, description, active, active ? loadedAt : null);
    }

    @PostMapping("/scenarios/{key}/load")
    @Operation(summary = "Load (deterministically seed) a scenario. Invalidates stale results.")
    public DatasetSummary loadScenario(@AuthenticationPrincipal AuthUser me, @PathVariable String key) {
        ScenarioKey sk;
        try {
            sk = ScenarioKey.valueOf(key);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown scenario.");
        }
        DatasetSnapshot s = scenarios.load(sk, me);
        return new DatasetSummary(s.scenario.name(), Labels.label(s.scenario), s.candidateCount,
                s.opportunityCount, s.seatCount, s.version, s.fingerprint, true);
    }

    // ---- Runs ----

    @GetMapping("/runs")
    @Operation(summary = "Decision history: all allocation runs, newest first.")
    public Page<RunSummary> runs(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        DatasetSnapshot current = snapshots.findTopByOrderByIdDesc().orElse(null);
        return runs.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)).map(r ->
                toRunSummary(r, current));
    }

    private RunSummary toRunSummary(AllocationRun r, DatasetSnapshot current) {
        Integer parentNumber = null;
        if (r.parentRunId != null) {
            parentNumber = runs.findById(r.parentRunId).map(p -> p.number).orElse(null);
        }
        boolean stale = current != null && !current.id.equals(r.snapshotId);
        return new RunSummary(r.id, r.number, r.runCode, Labels.label(r.scenario), r.policyName,
                Labels.label(r.status), r.solverStatus == null ? null : Labels.label(r.solverStatus),
                r.createdAt, r.completedAt, r.totalRuntimeMs, stale,
                r.parentRunId != null, parentNumber);
    }

    @PostMapping("/runs")
    @Operation(summary = "Start an allocation run (global optimization + sequential baseline).")
    public ResponseEntity<RunSummary> startRun(@AuthenticationPrincipal AuthUser me,
                                               @RequestBody(required = false) StartRunRequest req) {
        AllocationRun run = allocation.startRun(
                new AllocationService.StartRunRequest(req == null ? null : req.policyKey(),
                        req == null ? null : req.weights(),
                        req == null ? null : req.fullCoverage(),
                        req == null ? null : req.fairnessFloorPct()), me);
        DatasetSnapshot current = snapshots.findTopByOrderByIdDesc().orElse(null);
        return ResponseEntity.accepted().body(toRunSummary(run, current));
    }

    @PostMapping("/runs/{id}/cancel")
    @Operation(summary = "Cancel a queued or in-progress allocation run.")
    public RunSummary cancelRun(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        AllocationRun run = allocation.cancelRun(id, me);
        DatasetSnapshot current = snapshots.findTopByOrderByIdDesc().orElse(null);
        return toRunSummary(run, current);
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "Run detail: status, stage timings, comparison, provenance.")
    public RunDetail run(@PathVariable Long id) {
        AllocationRun r = allocation.requireRun(id);
        DatasetSnapshot current = snapshots.findTopByOrderByIdDesc().orElse(null);
        DatasetSnapshot snap = snapshots.findById(r.snapshotId).orElse(null);
        DatasetSummary dataset = snap == null ? null : new DatasetSummary(snap.scenario.name(),
                Labels.label(snap.scenario), snap.candidateCount, snap.opportunityCount,
                snap.seatCount, snap.version, snap.fingerprint, true);
        List<OverviewService.StageInfo> stages = parseStages(r.stageTimingsJson);
        return new RunDetail(toRunSummary(r, current), dataset,
                r.status == RunStatus.INFEASIBLE ? r.errorMessage : null, stages,
                provenance(r, snap));
    }

    private Provenance provenance(AllocationRun r, DatasetSnapshot snap) {
        Map<String, Double> weights = new LinkedHashMap<>();
        try {
            Weights w = Weights.parse(r.weightsJson, json);
            w.toMap().forEach(weights::put);
        } catch (Exception ignored) { }
        return new Provenance(r.runCode, r.number, Labels.label(r.scenario),
                snap != null ? String.valueOf(snap.version) : "—",
                snap != null ? snap.fingerprint : "—",
                snap != null ? snap.candidateCount : 0,
                snap != null ? snap.opportunityCount : 0,
                snap != null ? snap.seatCount : 0,
                snap != null ? snap.seed : 0,
                r.policyName, r.policyKey, r.policyVersion, weights,
                "OR-Tools CP-SAT (global) + deterministic sequential baseline",
                r.solverStatus == null ? null : Labels.label(r.solverStatus),
                r.objective, r.variables, r.constraints, r.solverRuntimeMs, r.totalRuntimeMs,
                1, 0L);
    }

    private List<OverviewService.StageInfo> parseStages(String s) {
        if (s == null || s.isBlank()) return List.of();
        try {
            JsonNode n = json.readTree(s);
            List<OverviewService.StageInfo> list = new ArrayList<>();
            for (JsonNode item : n) {
                list.add(new OverviewService.StageInfo(item.path("stage").asText(),
                        item.path("at").asText(), item.path("ms").asLong()));
            }
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping("/runs/{id}/comparison")
    @Operation(summary = "Strategy comparison: PRAGATI global vs sequential baseline.")
    public ComparisonService.ComparisonDto comparison(@PathVariable Long id) {
        return comparison.compare(id);
    }

    @GetMapping("/runs/{id}/conflicts")
    @Operation(summary = "Genuinely contested opportunities (eligible > seats).")
    public List<ConflictRow> conflicts(@PathVariable Long id) {
        java.util.Map<Long, Long> allocatedPerOpp = assignments
                .findByRunIdAndSource(id, in.pragati.domain.enums.AssignmentSource.GLOBAL).stream()
                .collect(java.util.stream.Collectors.groupingBy(a -> a.opportunityId, java.util.stream.Collectors.counting()));
        return conflicts.findByRunId(id).stream()
                .sorted((a, b) -> Double.compare(b.pressure, a.pressure))
                .map(c -> new ConflictRow(c.opportunityId,
                        opportunities.findById(c.opportunityId).map(o -> o.title).orElse("Opportunity"),
                        c.eligibleCount, c.seatCount,
                        allocatedPerOpp.getOrDefault(c.opportunityId, 0L).intValue(),
                        c.unmetDemand, c.pressure))
                .toList();
    }

    @GetMapping("/runs/{id}/movements")
    @Operation(summary = "Candidate movement between baseline and global allocation (paginated).")
    public Page<ComparisonService.MovementDto> movements(@PathVariable Long id,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "15") int size) {
        return comparison.movements(id, status, page, size);
    }

    @GetMapping("/runs/{id}/why/assigned")
    @Operation(summary = "Factor-level explanations for the run's assignments (for the explainability browser).")
    public List<ComparisonService.WhyDto> whyAssigned(@PathVariable Long id, @RequestParam(defaultValue = "40") int limit) {
        return comparison.whyAssigned(id, Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping("/runs/{id}/why/{candidateId}")
    @Operation(summary = "Why this allocation: factor-level evidence for one candidate.")
    public ComparisonService.WhyDto why(@PathVariable Long id, @PathVariable Long candidateId) {
        return comparison.why(id, candidateId);
    }

    @GetMapping("/runs/{id}/fairness")
    @Operation(summary = "Fairness outcomes for this run, with observed disparity explanation.")
    public FairnessDto fairness(@PathVariable Long id) {
        List<GroupMetric> all = groupMetrics.findByRunId(id);
        List<GroupRow> ruralUrban = all.stream().filter(g -> g.groupType.equals("RURAL_URBAN"))
                .map(g -> row(g)).toList();
        List<GroupRow> states = all.stream().filter(g -> g.groupType.equals("STATE"))
                .sorted((a, b) -> Integer.compare(b.population, a.population))
                .map(g -> row(g)).toList();
        List<GroupRow> quals = all.stream().filter(g -> g.groupType.equals("QUALIFICATION"))
                .map(g -> row(g)).toList();
        Disparity disparity = null;
        GroupMetric rural = all.stream().filter(g -> g.groupType.equals("RURAL_URBAN") && "Rural".equals(g.groupValue)).findFirst().orElse(null);
        GroupMetric urban = all.stream().filter(g -> g.groupType.equals("RURAL_URBAN") && "Urban".equals(g.groupValue)).findFirst().orElse(null);
        if (rural != null && urban != null && rural.population > 10 && urban.population > 10) {
            double gap = Math.round((urban.allocationRate - rural.allocationRate) * 10.0) / 10.0;
            String higher = gap >= 0 ? "Urban" : "Rural";
            String lower = gap >= 0 ? "Rural" : "Urban";
            disparity = new Disparity(higher, lower, Math.abs(gap),
                    "The " + lower + " group's allocation rate is " + Math.abs(gap)
                            + " percentage points below the " + higher + " group in this run. "
                            + "This is a measured outcome, not a guarantee; fairness floors in the Policy Lab "
                            + "can be used to respond to it.");
        } else {
            disparity = new Disparity("—", "—", 0, "Not enough data for a reliable comparison.");
        }
        String note = "Fairness is measured, never guaranteed. Groups with fewer than 10 members are excluded from disparity statements.";
        return new FairnessDto(ruralUrban, states, quals, disparity, note);
    }

    private GroupRow row(GroupMetric g) {
        return new GroupRow(g.groupValue, g.population, g.allocated, g.allocationRate,
                g.avgSuitability, g.preferenceSatisfaction);
    }

    @GetMapping("/runs/{id}/geography")
    @Operation(summary = "State-level demand, capacity, allocation, pressure and unmet demand (same data as the map).")
    public GeoDto geography(@PathVariable Long id) {
        List<GeoMetric> all = geoMetrics.findByRunId(id);
        List<GeoState> states = all.stream()
                .sorted((a, b) -> Integer.compare(b.demand, a.demand))
                .map(g -> new GeoState(g.state, g.demand, g.capacity, g.allocated,
                        g.unmetDemand, g.pressure, g.allocationRate))
                .toList();
        GeoTotals totals = new GeoTotals(
                all.stream().mapToInt(g -> g.demand).sum(),
                all.stream().mapToInt(g -> g.capacity).sum(),
                all.stream().mapToInt(g -> g.allocated).sum(),
                all.stream().mapToInt(g -> g.unmetDemand).sum());
        return new GeoDto(states, totals);
    }

    // ---- Policy & simulation ----

    @GetMapping("/policies")
    @Operation(summary = "PRAGATI demonstration policies (illustrative, not official).")
    public List<PolicyPreset> policies() {
        return policy.presets();
    }

    @PostMapping("/simulations")
    @Operation(summary = "Run a sandboxed what-if policy simulation against a completed run's dataset.")
    public ResponseEntity<Map<String, Object>> simulate(@AuthenticationPrincipal AuthUser me,
                                                        @RequestBody PolicyService.SimulateRequest req) {
        SimulationRun sim = policy.startSimulation(req, me);
        return ResponseEntity.accepted().body(Map.of("id", sim.id, "status", sim.status.name()));
    }

    @GetMapping("/simulations")
    @Operation(summary = "Simulation history.")
    public Page<Map<String, Object>> simulations(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return simulations.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)).map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id);
            m.put("baseRunId", s.baseRunId);
            m.put("policyName", s.policyName);
            m.put("status", Labels.label(s.status));
            m.put("solverStatus", s.solverStatus == null ? null : Labels.label(s.solverStatus));
            m.put("createdAt", s.createdAt);
            m.put("runtimeMs", s.runtimeMs);
            m.put("affected", s.affectedCount);
            m.put("stability", s.stability);
            m.put("metricsJson", s.metricsJson);
            return m;
        });
    }

    // ---- Reallocation ----

    @PostMapping("/reallocations")
    @Operation(summary = "Start a reallocation: operational changes + re-optimization (sandbox, parent-linked).")
    public ResponseEntity<RunSummary> reallocate(@AuthenticationPrincipal AuthUser me,
                                                 @RequestBody ReallocationService.ReallocationRequest req) {
        AllocationRun run = reallocation.start(req, me);
        DatasetSnapshot current = snapshots.findTopByOrderByIdDesc().orElse(null);
        return ResponseEntity.accepted().body(toRunSummary(run, current));
    }

    @GetMapping("/reallocations")
    @Operation(summary = "Reallocation runs (child runs with a parent).")
    public Page<RunSummary> reallocations(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        DatasetSnapshot current = snapshots.findTopByOrderByIdDesc().orElse(null);
        List<RunSummary> all = runs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 300))
                .map(r -> toRunSummary(r, current)).getContent().stream()
                .filter(RunSummary::reallocation).toList();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new org.springframework.data.domain.PageImpl<>(
                all.subList(from, to), PageRequest.of(page, size), all.size());
    }

    @GetMapping("/reallocations/{id}/diff")
    @Operation(summary = "Reallocation diff: parent allocation vs new allocation (moved/added/removed/unchanged).")
    public ReallocationService.DiffDto reallocationDiff(@PathVariable Long id) {
        return reallocation.diff(id);
    }

    // ---- Data quality, audit, judge, technical, report ----

    @GetMapping("/data-quality")
    @Operation(summary = "Dataset health checks: ready / attention / blocked, each with what, why, fix.")
    public List<DataQualityService.Check> dataQuality() {
        return dataQuality.checks();
    }

    @GetMapping("/audit")
    @Operation(summary = "Human-readable activity feed (login, allocation, simulation, reallocation, policy, scenario, admin changes).")
    public Page<AuditRow> audit(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return auditEvents.findAllByOrderByAtDesc(PageRequest.of(page, size)).map(e -> {
            Map<String, Object> details = null;
            if (e.detailsJson != null) {
                try {
                    details = json.readValue(e.detailsJson, Map.class);
                } catch (Exception ignored) { }
            }
            return new AuditRow(e.id, e.at.toString(), Labels.label(e.action),
                    e.actorName == null ? "System" : e.actorName, e.summary, details);
        });
    }

    @GetMapping("/judge/steps")
    @Operation(summary = "Judge Mode: the 14-step SIH narrative with live data.")
    public List<JudgeService.Step> judgeSteps() {
        return judge.steps();
    }

    @GetMapping("/technical")
    @Operation(summary = "Technical judge view: solver, AI mode, dataset metadata, stack, Swagger.")
    public Map<String, Object> technical() {
        return technical.technical();
    }

    @GetMapping("/runs/{id}/report")
    @Operation(summary = "Download the allocation report (PDF).")
    public ResponseEntity<byte[]> report(@PathVariable Long id) {
        byte[] pdf = report.generate(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pragati-allocation-report-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

}

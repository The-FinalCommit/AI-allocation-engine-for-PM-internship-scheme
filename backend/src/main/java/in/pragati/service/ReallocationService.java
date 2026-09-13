package in.pragati.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;


import com.fasterxml.jackson.databind.ObjectMapper;

import in.pragati.common.ApiException;
import in.pragati.domain.*;
import in.pragati.domain.enums.AssignmentSource;
import in.pragati.domain.enums.AuditAction;
import in.pragati.domain.enums.RunStatus;
import in.pragati.repo.*;
import in.pragati.security.AuthUser;

/**
 * Reallocation: previous optimized allocation + operational change
 * (capacity, withdrawals, policy) → new optimized allocation.
 * Sandbox by design: changes are run-scoped inputs; production data is
 * never mutated. The diff always compares parent allocation vs child
 * allocation (never the greedy baseline).
 */
@Service
public class ReallocationService {

    public record Change(String type, Long opportunityId, Long candidateId,
                         Integer newCapacity, Map<String, Double> weights,
                         Boolean fullCoverage, Integer fairnessFloorPct) { }

    public record ReallocationRequest(Long baseRunId, List<Change> changes) { }

    public record DiffEntry(Long candidateId, String candidateName, String before, String after,
                            double beforeScore, double afterScore, String status, String reason) { }

    public record DiffDto(Long runId, int runNumber, Long parentRunId, int parentRunNumber,
                          String changes, int moved, int added, int removed, int unchanged,
                          List<DiffEntry> entries) { }

    private final AllocationRunRepository runs;
    private final DatasetSnapshotRepository snapshots;
    private final CandidateProfileRepository profiles;
    private final OpportunityRepository opportunities;
    private final AssignmentRepository assignments;
    private final AllocationService allocation;
    private final AuditService audit;
    private final ObjectMapper json;

    public ReallocationService(AllocationRunRepository runs, DatasetSnapshotRepository snapshots,
                               CandidateProfileRepository profiles, OpportunityRepository opportunities,
                               AssignmentRepository assignments, AllocationService allocation,
                               AuditService audit, ObjectMapper json) {
        this.runs = runs; this.snapshots = snapshots; this.profiles = profiles;
        this.opportunities = opportunities; this.assignments = assignments;
        this.allocation = allocation; this.audit = audit; this.json = json;
    }

    public AllocationRun start(ReallocationRequest req, AuthUser actor) {
        AllocationRun base = runs.findById(req.baseRunId())
                .orElseThrow(() -> ApiException.notFound("The selected allocation run was not found."));
        if (base.status != RunStatus.COMPLETED) {
            throw ApiException.badRequest("Reallocation requires a completed allocation run.");
        }
        DatasetSnapshot current = snapshots.findTopByOrderByIdDesc().orElseThrow();
        if (!current.id.equals(base.snapshotId)) {
            throw ApiException.conflict("The dataset changed after this run completed. Run a fresh allocation first, then reallocate.");
        }
        if (req.changes() == null || req.changes().isEmpty()) {
            throw ApiException.badRequest("Select at least one operational change.");
        }
        Map<String, Object> overrides = new LinkedHashMap<>();
        List<String> changeDescriptions = new ArrayList<>();
        for (Change c : req.changes()) {
            switch (c.type()) {
                case "CAPACITY" -> {
                    Opportunity o = opportunities.findById(c.opportunityId())
                            .orElseThrow(() -> ApiException.badRequest("Unknown opportunity in change."));
                    if (c.newCapacity() == null || c.newCapacity() < 0 || c.newCapacity() > 500) {
                        throw ApiException.badRequest("Capacity must be between 0 and 500.");
                    }
                    @SuppressWarnings("unchecked")
                    Map<Integer, Integer> caps = (Map<Integer, Integer>) overrides.computeIfAbsent(
                            "capacityOverrides", k -> new LinkedHashMap<Integer, Integer>());
                    caps.put(o.id.intValue(), c.newCapacity());
                    changeDescriptions.add(o.title + ": seats changed from " + o.capacity + " to " + c.newCapacity());
                }
                case "WITHDRAW_CANDIDATE" -> {
                    CandidateProfile p = profiles.findById(c.candidateId())
                            .orElseThrow(() -> ApiException.badRequest("Unknown candidate in change."));
                    @SuppressWarnings("unchecked")
                    List<Long> wc = (List<Long>) overrides.computeIfAbsent(
                            "withdrawnCandidates", k -> new ArrayList<Long>());
                    wc.add(p.id);
                    changeDescriptions.add(p.fullName + " withdrew from the allocation.");
                }
                case "WITHDRAW_OPPORTUNITY" -> {
                    Opportunity o = opportunities.findById(c.opportunityId())
                            .orElseThrow(() -> ApiException.badRequest("Unknown opportunity in change."));
                    @SuppressWarnings("unchecked")
                    List<Long> wo = (List<Long>) overrides.computeIfAbsent(
                            "withdrawnOpportunities", k -> new ArrayList<Long>());
                    wo.add(o.id);
                    changeDescriptions.add(o.title + " was withdrawn.");
                }
                default -> throw ApiException.badRequest("Unknown change type: " + c.type());
            }
            // Policy change rides along with the run's policy (handled by StartRunRequest).
        }
        try {
            String overridesJson = overrides.isEmpty() ? null : json.writeValueAsString(overrides);
            String changesJson = json.writeValueAsString(changeDescriptions);
            AllocationRun parent = base;
            // Policy: honour an explicit POLICY change, otherwise reuse the parent's policy.
            AllocationService.StartRunRequest startReq;
            var policyChange = req.changes().stream()
                    .filter(c -> "POLICY".equals(c.type())).reduce((a, b) -> b).orElse(null);
            if (policyChange != null && policyChange.weights() != null && !policyChange.weights().isEmpty()) {
                startReq = new AllocationService.StartRunRequest(null, policyChange.weights(),
                        policyChange.fullCoverage, policyChange.fairnessFloorPct);
            } else if ("custom".equals(parent.policyKey)) {
                in.pragati.common.Weights parentW = in.pragati.common.Weights.parse(parent.weightsJson, json);
                java.util.Map<String, Double> w = new java.util.LinkedHashMap<>();
                parentW.toMap().forEach((k, v) -> w.put(k, v));
                startReq = new AllocationService.StartRunRequest(null, w,
                        parent.fullCoverage ? Boolean.TRUE : null, parent.fairnessFloorPct);
            } else {
                startReq = new AllocationService.StartRunRequest(parent.policyKey, null, null,
                        parent.fairnessFloorPct);
            }
            AllocationRun run = allocation.prepareRun(startReq, parent.id, overridesJson, actor);
            run.changesJson = changesJson;
            runs.save(run);
            allocation.submitForExecution(run);
            return run;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("The reallocation could not be prepared. Please check the selected changes.");
        }
    }

    private Change lastPolicyChange(List<Change> changes) {
        return changes.stream().filter(c -> "POLICY".equals(c.type())).reduce((a, b) -> b).orElse(null);
    }

    public Page<AllocationRun> reallocationRuns(int page, int size) {
        Page<AllocationRun> all = runs.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return all;
    }

    public DiffDto diff(Long runId) {
        AllocationRun child = runs.findById(runId)
                .orElseThrow(() -> ApiException.notFound("Allocation run not found."));
        if (child.parentRunId == null) {
            throw ApiException.badRequest("This run is not a reallocation, so there is no parent allocation to compare.");
        }
        AllocationRun parent = runs.findById(child.parentRunId)
                .orElseThrow(() -> ApiException.notFound("Parent allocation run not found."));
        List<Assignment> pList = assignments.findByRunIdAndSource(parent.id, AssignmentSource.GLOBAL);
        List<Assignment> cList = assignments.findByRunIdAndSource(child.id, AssignmentSource.GLOBAL);
        Map<Long, Assignment> p = new java.util.HashMap<>();
        for (Assignment a : pList) p.put(a.candidateId, a);
        Map<Long, Assignment> c = new java.util.HashMap<>();
        for (Assignment a : cList) c.put(a.candidateId, a);
        java.util.Set<Long> allCands = new java.util.TreeSet<>();
        allCands.addAll(p.keySet());
        allCands.addAll(c.keySet());

        int moved = 0, added = 0, removed = 0, unchanged = 0;
        List<DiffEntry> entries = new ArrayList<>();
        Map<Long, String> oppNames = new java.util.HashMap<>();
        for (Long cand : allCands) {
            Assignment pa = p.get(cand);
            Assignment ca = c.get(cand);
            String status;
            if (pa == null && ca != null) { status = "ADDED"; added++; }
            else if (pa != null && ca == null) { status = "REMOVED"; removed++; }
            else if (!pa.opportunityId.equals(ca.opportunityId)) { status = "MOVED"; moved++; }
            else { status = "UNCHANGED"; unchanged++; }
            String before = pa == null ? "Not allocated" : oppName(pa.opportunityId, oppNames);
            String after = ca == null ? "Not allocated" : oppName(ca.opportunityId, oppNames);
            String name = profiles.findById(cand).map(x -> x.fullName).orElse("Candidate");
            String reason = switch (status) {
                case "MOVED" -> "The operational change altered capacity or eligibility, so the global re-optimization moved this candidate.";
                case "ADDED" -> "Previously unallocated; the new allocation found a seat for this candidate.";
                case "REMOVED" -> "No longer allocated after the operational change.";
                default -> "Unaffected by the change.";
            };
            entries.add(new DiffEntry(cand, name, before, after,
                    pa != null ? pa.suitability : 0, ca != null ? ca.suitability : 0, status, reason));
        }
        String changes;
        try {
            changes = child.changesJson == null ? "No change description." : json.readTree(child.changesJson).toString();
        } catch (Exception e) {
            changes = "No change description.";
        }
        return new DiffDto(child.id, child.number, parent.id, parent.number,
                changes, moved, added, removed, unchanged, entries);
    }

    private String oppName(Long id, Map<Long, String> cache) {
        return cache.computeIfAbsent(id, x -> opportunities.findById(x).map(o -> o.title).orElse("Opportunity"));
    }

    public void auditReallocationCompletion(AllocationRun run) {
        if (run.parentRunId != null && run.status == RunStatus.COMPLETED) {
            audit.logSystem(AuditAction.REALLOCATION_COMPLETED, "AllocationRun", run.runCode,
                    "Reallocation run #" + run.number + " completed (parent run #" + run.parentRunId + ").", null);
        }
    }
}

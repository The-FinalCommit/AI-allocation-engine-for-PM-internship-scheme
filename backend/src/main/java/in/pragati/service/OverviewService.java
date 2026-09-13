package in.pragati.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.pragati.ai.AiServiceClient;
import in.pragati.common.Labels;
import in.pragati.domain.*;
import in.pragati.domain.enums.*;
import in.pragati.repo.*;

/** Assembles the Command Center (current state of the allocation system). */
@Service
public class OverviewService {

    public record ScenarioInfo(String key, String name, int candidateCount, int opportunityCount,
                               int seatCount, int version, String fingerprint, java.time.Instant loadedAt) { }
    public record ReadinessInfo(int ready, int partial, int incomplete, double average) { }
    public record AllocationInfo(int allocated, int unallocated, int candidates,
                                 double seatUtilization, double averageSuitability,
                                 double preferenceSatisfaction, int runNumber) { }
    public record StageInfo(String stage, String at, long ms) { }
    public record LatestRunInfo(Long id, int number, String code, String status, String solverStatus,
                                String scenario, java.time.Instant createdAt, Long totalRuntimeMs,
                                List<StageInfo> stages) { }
    public record Overview(ScenarioInfo scenario, ReadinessInfo readiness,
                           AllocationInfo allocation, LatestRunInfo latestRun, String aiMode) { }

    private final DatasetSnapshotRepository snapshots;
    private final CandidateProfileRepository profiles;
    private final CandidateSkillRepository skills;
    private final CandidateInterestRepository interests;
    private final CandidatePreferenceRepository preferences;
    private final ResumeFileRepository resumes;
    private final AllocationRunRepository runs;
    private final AssignmentRepository assignments;
    private final AiServiceClient ai;
    private final ObjectMapper json;

    public OverviewService(DatasetSnapshotRepository snapshots, CandidateProfileRepository profiles,
                           CandidateSkillRepository skills, CandidateInterestRepository interests,
                           CandidatePreferenceRepository preferences, ResumeFileRepository resumes,
                           AllocationRunRepository runs, AssignmentRepository assignments,
                           AiServiceClient ai, ObjectMapper json) {
        this.snapshots = snapshots; this.profiles = profiles; this.skills = skills;
        this.interests = interests; this.preferences = preferences; this.resumes = resumes;
        this.runs = runs; this.assignments = assignments; this.ai = ai; this.json = json;
    }

    public Overview overview() {
        DatasetSnapshot snap = snapshots.findTopByOrderByIdDesc().orElse(null);
        ScenarioInfo scenario = snap == null ? null : new ScenarioInfo(
                snap.scenario.name(), Labels.label(snap.scenario), snap.candidateCount,
                snap.opportunityCount, snap.seatCount, snap.version, snap.fingerprint, snap.createdAt);

        // Readiness buckets (calculated, never hardcoded)
        List<CandidateProfile> all = profiles.findAllByCandidateState(CandidateState.ACTIVE);
        List<Long> ids = all.stream().map(p -> p.id).toList();
        Set<Long> withSkills = skills.findByCandidateIdIn(ids).stream().map(s -> s.candidateId).collect(java.util.stream.Collectors.toSet());
        Set<Long> withPrefs = preferences.findByCandidateIdIn(ids).stream().map(s -> s.candidateId).collect(java.util.stream.Collectors.toSet());
        Set<Long> withInterests = interests.findByCandidateIdIn(ids).stream().map(s -> s.candidateId).collect(java.util.stream.Collectors.toSet());
        Set<Long> withResume = resumes.findAll().stream().filter(r -> "REVIEWED".equals(r.status))
                .map(r -> r.userId).collect(java.util.stream.Collectors.toSet());
        int ready = 0, partial = 0, sum = 0;
        for (CandidateProfile p : all) {
            int score = 0;
            if (p.phone != null && p.dob != null && p.state != null) score += 40; else if (p.state != null) score += 20;
            if (withSkills.contains(p.id)) score += 25;
            if (withPrefs.contains(p.id)) score += 15;
            if (withInterests.contains(p.id)) score += 10;
            if (withResume.contains(p.userId)) score += 10;
            sum += score;
            if (score >= 80) ready++;
            else if (score >= 40) partial++;
        }
        ReadinessInfo readiness = new ReadinessInfo(ready, partial, all.size() - ready - partial,
                all.isEmpty() ? 0 : Math.round((double) sum / all.size() * 10) / 10.0);

        // Allocation from the latest completed run on the CURRENT dataset version
        java.util.Optional<AllocationInfo> allocationOpt = latestCompletedCurrent(snap);
        AllocationInfo allocation = allocationOpt.orElse(null);

        LatestRunInfo latestRun = runs.findTopByOrderByIdDesc().map(r -> new LatestRunInfo(
                r.id, r.number, r.runCode,
                Labels.label(r.status), r.solverStatus == null ? null : Labels.label(r.solverStatus),
                Labels.label(r.scenario), r.createdAt, r.totalRuntimeMs, parseStages(r.stageTimingsJson))).orElse(null);

        String aiMode = "Deterministic engine (AI service offline)";
        try {
            AiServiceClient.AiInfo info = ai.info();
            aiMode = "deterministic-taxonomy".equalsIgnoreCase(info.aiMode())
                    ? "Deterministic taxonomy engine"
                    : (info.aiMode() == null ? aiMode : info.aiMode());
        } catch (Exception ignored) { }

        return new Overview(scenario, readiness, allocation, latestRun, aiMode);
    }

    private java.util.Optional<AllocationInfo> latestCompletedCurrent(DatasetSnapshot snap) {
        return runs.findTopByOrderByIdDesc()
                .filter(r -> r.status == RunStatus.COMPLETED)
                .filter(r -> snap == null || r.snapshotId.equals(snap.id))
                .map(r -> {
                    List<Assignment> g = assignments.findByRunIdAndSource(r.id, AssignmentSource.GLOBAL);
                    int cand = snap != null ? snap.candidateCount : 0;
                    long seats = snap != null ? snap.seatCount : 0;
                    double prefSat = 0;
                    if (!g.isEmpty()) {
                        List<Long> candIds = g.stream().map(a -> a.candidateId).toList();
                        Map<Long, Set<Long>> prefBy = new HashMap<>();
                        for (CandidatePreference cp : preferences.findByCandidateIdIn(candIds)) {
                            prefBy.computeIfAbsent(cp.candidateId, k -> new HashSet<>()).add(cp.opportunityId);
                        }
                        long sat = g.stream().filter(a -> prefBy.getOrDefault(a.candidateId, Set.of()).contains(a.opportunityId)).count();
                        prefSat = Math.round(sat * 1000.0 / g.size()) / 10.0;
                    }
                    return new AllocationInfo(g.size(), Math.max(0, cand - g.size()), cand,
                            seats > 0 ? Math.round(g.size() * 1000.0 / seats) / 10.0 : 0,
                            g.isEmpty() ? 0 : Math.round(g.stream().mapToDouble(a -> a.suitability).average().orElse(0) * 10) / 10.0,
                            prefSat, r.number);
                });
    }

    private List<StageInfo> parseStages(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return List.of();
        try {
            var node = json.readTree(jsonStr);
            java.util.List<StageInfo> list = new java.util.ArrayList<>();
            for (var n : node) {
                list.add(new StageInfo(n.path("stage").asText(), n.path("at").asText(), n.path("ms").asLong()));
            }
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }
}

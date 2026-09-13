package in.pragati.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.pragati.common.ApiException;
import in.pragati.domain.AllocationRun;
import in.pragati.domain.DatasetSnapshot;
import in.pragati.domain.enums.AuditAction;
import in.pragati.domain.enums.RunStatus;
import in.pragati.domain.enums.ScenarioKey;
import in.pragati.repo.AllocationRunRepository;
import in.pragati.repo.DatasetSnapshotRepository;
import in.pragati.security.AuthUser;
import in.pragati.seed.ScenarioSeeder;

/** Scenario lifecycle: load (seed) the active dataset and expose the current snapshot. */
@Service
public class ScenarioService {

    private final ScenarioSeeder seeder;
    private final DatasetSnapshotRepository snapshots;
    private final AllocationRunRepository runs;
    private final AuditService audit;

    public ScenarioService(ScenarioSeeder seeder, DatasetSnapshotRepository snapshots,
                           AllocationRunRepository runs, AuditService audit) {
        this.seeder = seeder;
        this.snapshots = snapshots;
        this.runs = runs;
        this.audit = audit;
    }

    public Optional<DatasetSnapshot> currentSnapshot() {
        return snapshots.findTopByOrderByIdDesc();
    }

    public void ensureNoActiveRun() {
        recoverStaleRuns();
        long active = runs.findAllByStatusIn(java.util.List.of(RunStatus.QUEUED, RunStatus.RUNNING)).size();
        if (active > 0) {
            throw ApiException.conflict("An allocation is already in progress. Wait for it to finish before changing the scenario.");
        }
    }

    /** Marks runs stuck in QUEUED/RUNNING (e.g. after an abrupt restart) as failed. */
    public void recoverStaleRuns() {
        java.time.Instant now = java.time.Instant.now();
        for (AllocationRun r : runs.findAllByStatusIn(java.util.List.of(RunStatus.QUEUED, RunStatus.RUNNING))) {
            java.time.Instant ref = r.startedAt != null ? r.startedAt : r.createdAt;
            long mins = java.time.Duration.between(ref, now).toMinutes();
            if (r.status == RunStatus.QUEUED && mins > 10 || r.status == RunStatus.RUNNING && mins > 15) {
                r.status = RunStatus.FAILED;
                r.errorMessage = "The run did not complete (application restart). Please start a new run.";
                r.completedAt = now;
                runs.save(r);
            }
        }
    }

    @Transactional
    public DatasetSnapshot load(ScenarioKey key, AuthUser actor) {
        ensureNoActiveRun();
        seeder.reset();
        DatasetSnapshot snapshot = seeder.seed(key);
        audit.log(actor, AuditAction.SCENARIO_LOADED, "Scenario", key.name(),
                "Scenario '" + in.pragati.common.Labels.label(key) + "' loaded with "
                        + snapshot.candidateCount + " candidates and "
                        + snapshot.opportunityCount + " opportunities.", null);
        return snapshot;
    }
}

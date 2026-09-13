package in.pragati.domain;

import jakarta.persistence.*;
import java.time.Instant;
import in.pragati.domain.enums.*;

/**
 * One execution of the allocation engine against one dataset snapshot with
 * one policy configuration. Reallocation runs link to their parent run and
 * carry the operational changes that triggered them.
 */
@Entity
@Table(name = "allocation_runs",
        uniqueConstraints = @UniqueConstraint(name = "uq_run_code", columnNames = "runCode"),
        indexes = {@Index(name = "idx_run_status", columnList = "status"),
                   @Index(name = "idx_run_parent", columnList = "parentRunId")})
public class AllocationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public String runCode;
    @Column(nullable = false)
    public int number;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    public ScenarioKey scenario;
    @Column(nullable = false)
    public Long snapshotId;
    @Column(nullable = false, length = 120)
    public String policyName;
    @Column(nullable = false, length = 40)
    public String policyKey;
    @Column(nullable = false, length = 20)
    public String policyVersion;
    @Column(nullable = false, length = 500)
    public String weightsJson;
    @Column(nullable = false)
    public boolean fullCoverage = false;
    public Integer fairnessFloorPct;
    @Column(length = 1000)
    public String fairnessGroupsJson;
    @Column(length = 2000)
    public String overridesJson;
    @Column(length = 1000)
    public String changesJson;
    public Long parentRunId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public RunStatus status = RunStatus.QUEUED;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    public SolverStatus solverStatus;
    public Long objective;
    public int eligiblePairs;
    public int variables;
    public int constraints;
    public long solverRuntimeMs;
    public long totalRuntimeMs;
    @Column(length = 2000)
    public String stageTimingsJson;
    @Column(length = 500)
    public String errorMessage;
    @Column(nullable = false, updatable = false)
    public Instant createdAt = Instant.now();
    public Instant startedAt;
    public Instant completedAt;
}

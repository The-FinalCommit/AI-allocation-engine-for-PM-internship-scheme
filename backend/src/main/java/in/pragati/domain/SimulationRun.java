package in.pragati.domain;

import jakarta.persistence.*;
import java.time.Instant;
import in.pragati.domain.enums.RunStatus;
import in.pragati.domain.enums.SolverStatus;

/**
 * A sandboxed what-if policy simulation. Never mutates the production
 * dataset; stores only aggregate outcomes and the affected-candidate diff.
 */
@Entity
@Table(name = "simulation_runs", indexes = {@Index(name = "idx_sim_base", columnList = "baseRunId")})
public class SimulationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long baseRunId;
    @Column(nullable = false, length = 120)
    public String policyName;
    @Column(nullable = false, length = 500)
    public String weightsJson;
    @Column(nullable = false)
    public boolean fullCoverage;
    public Integer fairnessFloorPct;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public RunStatus status = RunStatus.RUNNING;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    public SolverStatus solverStatus;
    @Column(length = 4000)
    public String metricsJson;
    public int affectedCount;
    public double stability;
    public long runtimeMs;
    @Column(nullable = false, updatable = false)
    public Instant createdAt = Instant.now();
}

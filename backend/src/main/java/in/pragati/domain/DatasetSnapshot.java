package in.pragati.domain;

import jakarta.persistence.*;
import java.time.Instant;
import in.pragati.domain.enums.ScenarioKey;

/**
 * Immutable description of the exact dataset an allocation run executed
 * against. Runs always reference one snapshot; a scenario re-seed creates a
 * new version and stale results are flagged, never silently reused.
 */
@Entity
@Table(name = "dataset_snapshots", indexes = {@Index(name = "idx_snapshot_scenario", columnList = "scenario")})
public class DatasetSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    public ScenarioKey scenario;
    @Column(nullable = false)
    public int version;
    @Column(nullable = false, length = 64)
    public String fingerprint;
    @Column(nullable = false)
    public long seed;
    @Column(nullable = false)
    public int candidateCount;
    @Column(nullable = false)
    public int opportunityCount;
    @Column(nullable = false)
    public int seatCount;
    @Column(nullable = false, updatable = false)
    public Instant createdAt = Instant.now();
}

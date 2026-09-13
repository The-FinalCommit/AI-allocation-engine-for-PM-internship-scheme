package in.pragati.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "conflict_metrics", indexes = {@Index(name = "idx_conflict_run", columnList = "runId")})
public class ConflictMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long runId;
    @Column(nullable = false)
    public Long opportunityId;
    @Column(nullable = false)
    public int eligibleCount;
    @Column(nullable = false)
    public int seatCount;
    @Column(nullable = false)
    public int unmetDemand;
    public double pressure;
}

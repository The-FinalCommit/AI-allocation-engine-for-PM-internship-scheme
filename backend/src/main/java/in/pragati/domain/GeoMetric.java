package in.pragati.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "geo_metrics", indexes = {@Index(name = "idx_geo_run", columnList = "runId")})
public class GeoMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long runId;
    @Column(nullable = false, length = 60)
    public String state;
    @Column(nullable = false)
    public int demand;
    @Column(nullable = false)
    public int capacity;
    @Column(nullable = false)
    public int allocated;
    @Column(nullable = false)
    public int unmetDemand;
    public double pressure;
    public double allocationRate;
}

package in.pragati.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "group_metrics", indexes = {@Index(name = "idx_group_run", columnList = "runId")})
public class GroupMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long runId;
    @Column(nullable = false, length = 40)
    public String groupType;
    @Column(nullable = false, length = 80)
    public String groupValue;
    @Column(nullable = false)
    public int population;
    @Column(nullable = false)
    public int allocated;
    public double avgSuitability;
    public double preferenceSatisfaction;
    public double allocationRate;
}

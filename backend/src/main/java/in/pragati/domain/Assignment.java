package in.pragati.domain;

import jakarta.persistence.*;
import in.pragati.domain.enums.AssignmentSource;

/**
 * One candidate-opportunity assignment for one run and one strategy
 * (GLOBAL = PRAGATI allocation, BASELINE = sequential reference).
 */
@Entity
@Table(name = "assignments",
        uniqueConstraints = @UniqueConstraint(name = "uq_assign_run_cand_src", columnNames = {"runId", "candidateId", "source"}),
        indexes = {@Index(name = "idx_assign_run", columnList = "runId"),
                   @Index(name = "idx_assign_candidate", columnList = "candidateId"),
                   @Index(name = "idx_assign_opp", columnList = "opportunityId")})
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long runId;
    @Column(nullable = false)
    public Long candidateId;
    @Column(nullable = false)
    public Long opportunityId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public AssignmentSource source;
    @Column(nullable = false)
    public double suitability;
    @Column(nullable = false)
    public double skillsFit;
    @Column(nullable = false)
    public double qualificationFit;
    @Column(nullable = false)
    public double interestFit;
    @Column(nullable = false)
    public double locationFit;
    @Column(nullable = false)
    public double preferenceFit;
    @Column(nullable = false)
    public double learningFit;
    @Column(nullable = false)
    public double experienceFit;
    @Column(length = 800)
    public String breakdownJson;
    @Column(length = 600)
    public String reason;
}

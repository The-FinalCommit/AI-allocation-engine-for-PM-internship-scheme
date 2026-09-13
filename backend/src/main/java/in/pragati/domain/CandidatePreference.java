package in.pragati.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "candidate_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uq_pref_cand_opp", columnNames = {"candidateId", "opportunityId"}),
        indexes = {@Index(name = "idx_pref_candidate", columnList = "candidateId"),
                   @Index(name = "idx_pref_opportunity", columnList = "opportunityId")})
public class CandidatePreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long candidateId;
    @Column(nullable = false)
    public Long opportunityId;
    @Column(nullable = false)
    public int rank;
    @Column(nullable = false, updatable = false)
    public Instant createdAt = Instant.now();
}

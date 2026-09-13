package in.pragati.domain;

import jakarta.persistence.*;
import in.pragati.domain.enums.Sector;

@Entity
@Table(name = "candidate_interests", indexes = {@Index(name = "idx_interest_candidate", columnList = "candidateId")})
public class CandidateInterest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long candidateId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    public Sector sector;
}

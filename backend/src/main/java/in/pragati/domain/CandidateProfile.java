package in.pragati.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import in.pragati.domain.enums.*;

@Entity
@Table(name = "candidate_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uq_candidate_user", columnNames = "user_id"),
        indexes = {@Index(name = "idx_candidate_state", columnList = "state"),
                   @Index(name = "idx_candidate_cstate", columnList = "candidateState")})
public class CandidateProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long userId;
    @Column(nullable = false, length = 120)
    public String fullName;
    @Column(length = 30)
    public String phone;
    public LocalDate dob;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    public Qualification qualification;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    public CandidateStatus candidateStatus;
    @Column(length = 60)
    public String state;
    @Column(length = 60)
    public String district;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    public LocationType locationType;
    @Column(length = 600)
    public String bio;
    @Column(nullable = false)
    public Double experienceYears = 0.0;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public CandidateState candidateState = CandidateState.ACTIVE;
    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    public void touch() { this.updatedAt = Instant.now(); }
}

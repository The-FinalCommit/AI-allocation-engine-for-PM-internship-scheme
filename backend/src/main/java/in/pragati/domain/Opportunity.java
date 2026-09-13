package in.pragati.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import in.pragati.domain.enums.OppStatus;
import in.pragati.domain.enums.Qualification;
import in.pragati.domain.enums.Sector;

@Entity
@Table(name = "opportunities",
        indexes = {@Index(name = "idx_opp_provider", columnList = "providerId"),
                   @Index(name = "idx_opp_state", columnList = "state"),
                   @Index(name = "idx_opp_sector", columnList = "sector")})
public class Opportunity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long providerId;
    @Column(nullable = false, length = 160)
    public String title;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    public Sector sector;
    @Column(nullable = false, length = 60)
    public String state;
    @Column(length = 80)
    public String city;
    @Column(nullable = false)
    public int capacity;
    @Column(nullable = false)
    public int durationMonths;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public Qualification minQualification;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public OppStatus status = OppStatus.ACTIVE;
    @Column(length = 800)
    public String description;
    @Column(nullable = false, updatable = false)
    public Instant createdAt = Instant.now();
    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    public void touch() { this.updatedAt = Instant.now(); }
}

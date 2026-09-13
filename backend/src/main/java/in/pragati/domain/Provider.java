package in.pragati.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "providers", uniqueConstraints = @UniqueConstraint(name = "uq_provider_user", columnNames = "user_id"))
public class Provider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long userId;
    @Column(nullable = false, length = 160)
    public String orgName;
    @Column(length = 80)
    public String orgType;
    @Column(length = 60)
    public String state;
    @Column(length = 600)
    public String about;
    @Column(nullable = false, updatable = false)
    public Instant createdAt = Instant.now();
}

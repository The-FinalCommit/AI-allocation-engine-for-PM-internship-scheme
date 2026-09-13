package in.pragati.domain;

import jakarta.persistence.*;
import java.time.Instant;
import in.pragati.domain.enums.Role;
import in.pragati.domain.enums.UserStatus;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uq_users_email", columnNames = "email"))
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, length = 190)
    public String email;
    @Column(nullable = false)
    public String passwordHash;
    @Column(nullable = false, length = 120)
    public String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Role role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public UserStatus status = UserStatus.ACTIVE;
    @Column(nullable = false, updatable = false)
    public Instant createdAt = Instant.now();
}

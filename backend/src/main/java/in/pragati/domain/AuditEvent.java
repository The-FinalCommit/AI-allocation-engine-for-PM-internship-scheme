package in.pragati.domain;

import jakarta.persistence.*;
import java.time.Instant;
import in.pragati.domain.enums.AuditAction;

@Entity
@Table(name = "audit_events", indexes = {@Index(name = "idx_audit_at", columnList = "at")})
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long actorUserId;
    @Column(length = 120)
    public String actorName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    public AuditAction action;
    @Column(length = 40)
    public String targetType;
    @Column(length = 60)
    public String targetId;
    @Column(nullable = false, length = 300)
    public String summary;
    @Column(length = 1500)
    public String detailsJson;
    @Column(nullable = false, updatable = false)
    public Instant at = Instant.now();
}

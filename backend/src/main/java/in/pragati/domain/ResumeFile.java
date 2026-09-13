package in.pragati.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "resume_files", indexes = {@Index(name = "idx_resume_user", columnList = "userId")})
public class ResumeFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long userId;
    @Column(nullable = false, length = 255)
    public String originalName;
    @Column(nullable = false, length = 255)
    public String storedName;
    @Column(length = 120)
    public String contentType;
    @Column(nullable = false)
    public long sizeBytes;
    /** UPLOADED → PROCESSING → PENDING_REVIEW → REVIEWED (or FAILED).
     *  PENDING_REVIEW means the candidate still has to confirm suggestions. */
    @Column(nullable = false, length = 20)
    public String status = "UPLOADED";
    @Column(length = 2000)
    public String skillsJson;
    /** Structured profile suggestions: [{field, value, confidence, evidence}, ...] */
    @Column(length = 8000)
    public String suggestionsJson;
    @Column(length = 500)
    public String statusMessage;
    public int textLength;
    @Column(nullable = false, updatable = false)
    public Instant createdAt = Instant.now();
}

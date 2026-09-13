package in.pragati.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Versioned embedding cache. Keyed by entity, model version and content
 * fingerprint so cached vectors are only reused for identical content and
 * model generations.
 */
@Entity
@Table(name = "embedding_cache",
        uniqueConstraints = @UniqueConstraint(name = "uq_embed_key", columnNames = {"entityKey", "modelVersion", "fingerprint"}),
        indexes = {@Index(name = "idx_embed_key", columnList = "entityKey")})
public class EmbeddingCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, length = 120)
    public String entityKey;
    @Column(nullable = false, length = 40)
    public String modelVersion;
    @Column(nullable = false, length = 64)
    public String fingerprint;
    @Column(nullable = false, length = 4000)
    public String vectorJson;
    @Column(nullable = false)
    public int dims;
    @Column(nullable = false, updatable = false)
    public Instant createdAt = Instant.now();
}

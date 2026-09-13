package in.pragati.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.EmbeddingCache;
import java.util.Optional;

public interface EmbeddingCacheRepository extends JpaRepository<EmbeddingCache, Long> {
    Optional<EmbeddingCache> findByEntityKeyAndModelVersionAndFingerprint(String entityKey, String modelVersion, String fingerprint);
}

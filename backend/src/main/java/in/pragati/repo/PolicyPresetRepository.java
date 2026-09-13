package in.pragati.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.PolicyPreset;
import java.util.Optional;

public interface PolicyPresetRepository extends JpaRepository<PolicyPreset, Long> {
    Optional<PolicyPreset> findByKey(String key);
}

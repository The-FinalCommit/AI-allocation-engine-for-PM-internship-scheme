package in.pragati.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.Provider;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    Optional<Provider> findByUserId(Long userId);
}

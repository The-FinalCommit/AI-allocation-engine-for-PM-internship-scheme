package in.pragati.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.DatasetSnapshot;
import in.pragati.domain.enums.ScenarioKey;
import java.util.Optional;

public interface DatasetSnapshotRepository extends JpaRepository<DatasetSnapshot, Long> {
    Optional<DatasetSnapshot> findTopByOrderByIdDesc();
    long countByScenario(ScenarioKey scenario);
}

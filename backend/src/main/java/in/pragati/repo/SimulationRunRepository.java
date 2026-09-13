package in.pragati.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.SimulationRun;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, Long> {
    org.springframework.data.domain.Page<SimulationRun> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);
}

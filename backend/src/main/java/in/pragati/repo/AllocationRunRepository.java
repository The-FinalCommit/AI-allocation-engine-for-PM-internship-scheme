package in.pragati.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.AllocationRun;
import in.pragati.domain.enums.RunStatus;
import java.util.List;
import java.util.Optional;

public interface AllocationRunRepository extends JpaRepository<AllocationRun, Long> {
    Optional<AllocationRun> findTopByOrderByIdDesc();
    Optional<AllocationRun> findTopByStatusOrderByIdDesc(RunStatus status);
    List<AllocationRun> findAllByStatusIn(List<RunStatus> statuses);
    Optional<AllocationRun> findTopByParentRunIdOrderByIdDesc(Long parentRunId);
    Page<AllocationRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Optional<AllocationRun> findByRunCode(String runCode);
}

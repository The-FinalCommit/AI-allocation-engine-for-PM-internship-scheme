package in.pragati.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.Assignment;
import in.pragati.domain.enums.AssignmentSource;
import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByRunIdAndSource(Long runId, AssignmentSource source);
    Page<Assignment> findByRunIdAndSource(Long runId, AssignmentSource source, Pageable pageable);
    Optional<Assignment> findByRunIdAndCandidateIdAndSource(Long runId, Long candidateId, AssignmentSource source);
    long countByRunIdAndSource(Long runId, AssignmentSource source);
    List<Assignment> findByCandidateIdAndSource(Long candidateId, AssignmentSource source);
    List<Assignment> findByRunIdAndSourceAndOpportunityId(Long runId, AssignmentSource source, Long opportunityId);
    void deleteByRunId(Long runId);
}

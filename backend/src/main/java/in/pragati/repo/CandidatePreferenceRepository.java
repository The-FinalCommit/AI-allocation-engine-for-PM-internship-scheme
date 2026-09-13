package in.pragati.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import in.pragati.domain.CandidatePreference;
import java.util.List;
import java.util.Optional;

public interface CandidatePreferenceRepository extends JpaRepository<CandidatePreference, Long> {
    List<CandidatePreference> findByCandidateId(Long candidateId);
    List<CandidatePreference> findByCandidateIdIn(List<Long> candidateIds);
    Optional<CandidatePreference> findByCandidateIdAndOpportunityId(Long candidateId, Long opportunityId);

    /**
     * Bulk (HQL) delete: executes immediately against the database within the
     * current transaction. A derived delete would queue the row deletions
     * AFTER the new preference inserts in the same flush, violating the
     * (candidateId, opportunityId) unique constraint — which is why saving
     * preferences used to fail.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from CandidatePreference cp where cp.candidateId = :candidateId")
    void deleteByCandidateId(@Param("candidateId") Long candidateId);
}

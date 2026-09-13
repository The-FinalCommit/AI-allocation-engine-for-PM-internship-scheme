package in.pragati.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import in.pragati.domain.Opportunity;
import in.pragati.domain.enums.OppStatus;
import in.pragati.domain.enums.Qualification;
import in.pragati.domain.enums.Sector;
import java.util.List;
import java.util.Optional;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {
    Page<Opportunity> findByStatus(OppStatus status, Pageable pageable);
    List<Opportunity> findByProviderId(Long providerId);
    List<Opportunity> findAllByStatus(OppStatus status);
    long countByStatus(OppStatus status);
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(o.capacity), 0) from Opportunity o where o.status = :status")
    long sumCapacityByStatus(@Param("status") OppStatus status);
    List<Opportunity> findByStateIn(List<String> states);
    Optional<Opportunity> findByIdAndProviderId(Long id, Long providerId);
    List<Opportunity> findTop20ByOrderByCreatedAtDesc();
}

package in.pragati.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.CandidateProfile;
import in.pragati.domain.enums.CandidateState;
import java.util.List;
import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {
    Optional<CandidateProfile> findByUserId(Long userId);
    List<CandidateProfile> findAllByCandidateState(CandidateState state);
    long countByCandidateState(CandidateState state);
}

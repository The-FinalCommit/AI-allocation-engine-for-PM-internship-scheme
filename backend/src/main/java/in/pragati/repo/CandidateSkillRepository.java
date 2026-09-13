package in.pragati.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.CandidateSkill;
import java.util.List;

public interface CandidateSkillRepository extends JpaRepository<CandidateSkill, Long> {
    List<CandidateSkill> findByCandidateId(Long candidateId);
    List<CandidateSkill> findByCandidateIdIn(List<Long> candidateIds);
    void deleteByCandidateId(Long candidateId);
}

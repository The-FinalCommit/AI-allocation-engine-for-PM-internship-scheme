package in.pragati.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.ResumeFile;
import java.util.List;
import java.util.Optional;

public interface ResumeFileRepository extends JpaRepository<ResumeFile, Long> {
    List<ResumeFile> findByUserIdOrderByIdDesc(Long userId);
    Optional<ResumeFile> findTopByUserIdOrderByIdDesc(Long userId);
}

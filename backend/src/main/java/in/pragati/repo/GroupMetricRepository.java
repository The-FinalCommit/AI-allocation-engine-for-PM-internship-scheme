package in.pragati.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.GroupMetric;
import java.util.List;

public interface GroupMetricRepository extends JpaRepository<GroupMetric, Long> {
    List<GroupMetric> findByRunId(Long runId);
    void deleteByRunId(Long runId);
}

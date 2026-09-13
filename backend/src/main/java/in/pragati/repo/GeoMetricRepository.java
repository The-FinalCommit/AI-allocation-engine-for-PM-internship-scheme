package in.pragati.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import in.pragati.domain.GeoMetric;
import java.util.List;

public interface GeoMetricRepository extends JpaRepository<GeoMetric, Long> {
    List<GeoMetric> findByRunId(Long runId);
    void deleteByRunId(Long runId);
}

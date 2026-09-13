package in.pragati.seed;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import in.pragati.domain.DatasetSnapshot;
import in.pragati.domain.enums.ScenarioKey;
import in.pragati.repo.DatasetSnapshotRepository;
import in.pragati.service.AuditService;

/**
 * First-boot bootstrap: seeds the Standard Showcase scenario so a fresh
 * user can sign in and see a fully populated (synthetic) environment
 * without any manual data entry.
 */
@Configuration
public class StartupSeeder {

    @Bean
    @Order(100)
    public ApplicationRunner seedInitialScenario(DatasetSnapshotRepository snapshots,
                                                 ScenarioSeeder seeder, AuditService audit) {
        return args -> {
            if (snapshots.count() > 0) return;
            DatasetSnapshot snapshot = seeder.seed(ScenarioKey.STANDARD_SHOWCASE);
            audit.logSystem(in.pragati.domain.enums.AuditAction.ADMIN_ACTION, "Scenario", ScenarioKey.STANDARD_SHOWCASE.name(),
                    "Initial environment seeded with the Standard Showcase dataset ("
                            + snapshot.candidateCount + " candidates, "
                            + snapshot.opportunityCount + " opportunities).", null);
        };
    }
}

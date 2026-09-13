package in.pragati.seed;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import in.pragati.domain.PolicyPreset;
import in.pragati.repo.PolicyPresetRepository;

/**
 * PRAGATI demonstration policies (illustrative, not official government
 * policy). Weights always sum to 100.
 */
@Configuration
public class PolicySeeder {

    @Bean
    public ApplicationRunner seedPolicies(PolicyPresetRepository presets) {
        return args -> {
            if (presets.count() > 0) return;
            presets.save(preset("balanced", "Balanced Allocation Policy",
                    "PRAGATI default configurable weights that balance skill fit with preference and location.",
                    "{\"skills\":35,\"qualification\":15,\"interest\":15,\"location\":10,\"preference\":10,\"learning\":10,\"experience\":5}",
                    false, null, true, "v1"));
            presets.save(preset("skills", "Skills-Focused Policy",
                    "Demonstration policy that prioritises technical skill fit over other factors.",
                    "{\"skills\":50,\"qualification\":10,\"interest\":10,\"location\":10,\"preference\":10,\"learning\":5,\"experience\":5}",
                    false, null, false, "v1"));
            presets.save(preset("preference", "Preference-Focused Policy",
                    "Demonstration policy that gives candidates more control over where they are allocated.",
                    "{\"skills\":25,\"qualification\":10,\"interest\":20,\"location\":10,\"preference\":25,\"learning\":5,\"experience\":5}",
                    false, null, false, "v1"));
            presets.save(preset("equity", "Equity-Oriented Policy",
                    "Demonstration policy that weighs location and learning potential more heavily to spread opportunities.",
                    "{\"skills\":30,\"qualification\":15,\"interest\":10,\"location\":15,\"preference\":10,\"learning\":10,\"experience\":10}",
                    false, null, false, "v1"));
            presets.save(preset("full_coverage", "Full Coverage (Demonstration)",
                    "Demonstration policy with a hard rule that every eligible candidate must be allocated. "
                            + "Infeasible when demand exceeds available seats — used to show real solver infeasibility.",
                    "{\"skills\":35,\"qualification\":15,\"interest\":15,\"location\":10,\"preference\":10,\"learning\":10,\"experience\":5}",
                    true, null, false, "v1"));
        };
    }

    private PolicyPreset preset(String key, String name, String description, String weights,
                                boolean fullCoverage, Integer floor, boolean isDefault, String version) {
        PolicyPreset p = new PolicyPreset();
        p.key = key;
        p.name = name;
        p.description = description;
        p.weightsJson = weights;
        p.fullCoverage = fullCoverage;
        p.fairnessFloorPct = floor;
        p.isDefault = isDefault;
        p.version = version;
        return p;
    }
}

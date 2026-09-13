package in.pragati.ai;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import in.pragati.common.ApiException;
import in.pragati.config.AppProperties;

/**
 * Client for the PRAGATI AI service (Python/FastAPI): global optimization
 * (OR-Tools CP-SAT + deterministic sequential baseline), resume skill
 * extraction and skill normalization.
 */
@Service
public class AiServiceClient {

    private final RestTemplate rest;
    private final String baseUrl;

    public AiServiceClient(AppProperties props) {
        ClientHttpRequestFactory factory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(5))
                        .withReadTimeout(Duration.ofSeconds(props.getOptimizer().getTimeoutSeconds())));
        this.rest = new RestTemplate(factory);
        this.baseUrl = props.getAi().getServiceUrl();
    }

    // ------------------------------------------------------------------
    // DTOs
    // ------------------------------------------------------------------

    public record AiInfo(String aiMode, String embeddingModel, String engineDescription,
                         String optimizer, String optimizerVersion, String version) { }

    public record OptOpp(int id, int capacity) { }
    public record OptPair(int c, int i, int score, int pref) { }
    public record OptGroup(String key, List<Integer> candidates, int floorPct) { }
    public record OptPolicy(boolean fullCoverage, List<OptGroup> groups) { }
    public record OptParams(int maxTimeSeconds, int workers, long seed) { }
    public record OptimizeRequest(List<Integer> candidates, List<OptOpp> opportunities,
                                  List<OptPair> pairs, OptPolicy policy, OptParams params) { }

    public record ModelStats(int pairs, int variables, int constraints, long solverRuntimeMs) { }
    public record OptAssignment(int c, int i, int score) { }
    public record OptimizeResult(String status, long objective, long runtimeMs,
                                 ModelStats model, List<OptAssignment> global, List<OptAssignment> greedy) { }

    public record ExtractedSkill(String raw, String canonical, double confidence) { }
    public record SkillsResult(List<ExtractedSkill> skills, String engine) { }

    public record GapFactor(String key, String label, double fit, double weight,
                            double contribution, double lostPoints) { }
    public record GapGuidanceRequest(String question, String skill, String opportunityTitle,
                                     String sector, List<String> mandatorySkills,
                                     List<String> preferredSkills, List<String> candidateSkills,
                                     boolean eligible, List<GapFactor> factors, double overall) { }
    public record GapGuidanceResult(String answer, String engine, String grounding) { }

    // ------------------------------------------------------------------
    // Calls
    // ------------------------------------------------------------------

    public boolean isAvailable() {
        try {
            rest.getForEntity(baseUrl + "/health", String.class);
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    public AiInfo info() {
        try {
            return rest.getForEntity(baseUrl + "/info", AiInfo.class).getBody();
        } catch (RestClientException e) {
            throw new IllegalStateException("AI service unavailable", e);
        }
    }

    public OptimizeResult optimize(OptimizeRequest request) {
        try {
            return rest.postForObject(baseUrl + "/optimize", request, OptimizeResult.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Optimization service call failed", e);
        }
    }

    public SkillsResult extractSkills(String text) {
        try {
            return rest.postForObject(baseUrl + "/nlp/skills",
                    java.util.Map.of("text", text == null ? "" : text), SkillsResult.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Skill extraction call failed", e);
        }
    }

    public GapGuidanceResult gapGuidance(GapGuidanceRequest req) {
        try {
            return rest.postForObject(baseUrl + "/nlp/gap-guidance", req, GapGuidanceResult.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Gap guidance call failed", e);
        }
    }
}

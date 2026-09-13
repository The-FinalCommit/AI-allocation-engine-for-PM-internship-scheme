package in.pragati.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import in.pragati.ai.AiServiceClient;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Health", description = "Service health for the demonstration environment.")
public class HealthController {

    private final AiServiceClient ai;

    public HealthController(AiServiceClient ai) { this.ai = ai; }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "UP");
        out.put("aiService", ai.isAvailable() ? "UP" : "DOWN");
        return out;
    }
}

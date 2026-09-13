package in.pragati.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PRAGATI default configurable weights (illustrative, not official
 * government weights). Always sum to 100.
 */
public record Weights(double skills, double qualification, double interest,
                      double location, double preference, double learning, double experience) {

    public static final String[] KEYS = {"skills", "qualification", "interest", "location", "preference", "learning", "experience"};

    public static Weights defaultBalanced() {
        return new Weights(35, 15, 15, 10, 10, 10, 5);
    }

    public double sum() {
        return skills + qualification + interest + location + preference + learning + experience;
    }

    public Map<String, Double> toMap() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("skills", skills);
        m.put("qualification", qualification);
        m.put("interest", interest);
        m.put("location", location);
        m.put("preference", preference);
        m.put("learning", learning);
        m.put("experience", experience);
        return m;
    }

    public String toJson(ObjectMapper json) {
        try {
            return json.writeValueAsString(toMap());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize weights", e);
        }
    }

    public static Weights parse(String json, ObjectMapper mapper) {
        try {
            JsonNode n = mapper.readTree(json);
            Weights w = new Weights(
                    n.path("skills").asDouble(0), n.path("qualification").asDouble(0),
                    n.path("interest").asDouble(0), n.path("location").asDouble(0),
                    n.path("preference").asDouble(0), n.path("learning").asDouble(0),
                    n.path("experience").asDouble(0));
            if (Math.abs(w.sum() - 100) > 0.001) {
                throw ApiException.badRequest("Policy weights must total exactly 100%.");
            }
            return w;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid policy weights.");
        }
    }
}

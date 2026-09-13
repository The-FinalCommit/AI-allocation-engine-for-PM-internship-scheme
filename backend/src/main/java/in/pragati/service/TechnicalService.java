package in.pragati.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import in.pragati.ai.AiServiceClient;
import in.pragati.common.Labels;
import in.pragati.domain.AllocationRun;
import in.pragati.domain.DatasetSnapshot;
import in.pragati.repo.AllocationRunRepository;
import in.pragati.repo.DatasetSnapshotRepository;

/** Technical judge view: where implementation details are allowed to live. */
@Service
public class TechnicalService {

    private final AiServiceClient ai;
    private final AllocationRunRepository runs;
    private final DatasetSnapshotRepository snapshots;

    public TechnicalService(AiServiceClient ai, AllocationRunRepository runs,
                            DatasetSnapshotRepository snapshots) {
        this.ai = ai; this.runs = runs; this.snapshots = snapshots;
    }

    public Map<String, Object> technical() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> aiBlock = new LinkedHashMap<>();
        try {
            AiServiceClient.AiInfo info = ai.info();
            aiBlock.put("status", "online");
            aiBlock.put("aiMode", info.aiMode());
            aiBlock.put("engineDescription", info.engineDescription());
            aiBlock.put("embeddingModel", info.embeddingModel());
            aiBlock.put("optimizer", info.optimizer());
            aiBlock.put("optimizerVersion", info.optimizerVersion());
            aiBlock.put("serviceVersion", info.version());
        } catch (Exception e) {
            aiBlock.put("status", "offline");
            aiBlock.put("note", "The AI/optimization service is not reachable. Core data features remain available; allocation runs require the service.");
        }
        out.put("ai", aiBlock);

        Map<String, Object> solver = new LinkedHashMap<>();
        runs.findTopByOrderByIdDesc().ifPresent(r -> {
            solver.put("lastRun", "#" + r.number);
            solver.put("solverStatus", r.solverStatus == null ? "—" : Labels.label(r.solverStatus));
            solver.put("objective", r.objective);
            solver.put("eligiblePairs", r.eligiblePairs);
            solver.put("variables", r.variables);
            solver.put("constraints", r.constraints);
            solver.put("solverRuntimeMs", r.solverRuntimeMs);
            solver.put("totalRuntimeMs", r.totalRuntimeMs);
        });
        out.put("solver", solver);

        Map<String, Object> dataset = new LinkedHashMap<>();
        snapshots.findTopByOrderByIdDesc().ifPresent(s -> {
            dataset.put("scenario", Labels.label(s.scenario));
            dataset.put("version", s.version);
            dataset.put("fingerprint", s.fingerprint);
            dataset.put("seed", s.seed);
            dataset.put("candidates", s.candidateCount);
            dataset.put("opportunities", s.opportunityCount);
            dataset.put("seats", s.seatCount);
        });
        out.put("dataset", dataset);

        Map<String, Object> stack = new LinkedHashMap<>();
        stack.put("frontend", "React 18 + TypeScript + Vite + Tailwind CSS + Recharts + Leaflet");
        stack.put("backend", "Java 17 · Spring Boot 3 (Web, Security, Data JPA, Validation, Actuator, SpringDoc)");
        stack.put("aiService", "Python · FastAPI · deterministic skill normalization (taxonomy matching) · global optimization engine");
        stack.put("optimizer", "Google OR-Tools CP-SAT (deterministic greedy baseline included)");
        stack.put("database", "H2 (demo) · MySQL 8 (production profile)");
        out.put("stack", stack);

        Map<String, Object> links = new LinkedHashMap<>();
        links.put("swagger", "/swagger-ui.html");
        links.put("openApiJson", "/v3/api-docs");
        out.put("links", links);
        return out;
    }
}

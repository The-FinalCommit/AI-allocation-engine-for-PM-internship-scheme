package in.pragati.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.pragati.domain.AuditEvent;
import in.pragati.domain.enums.AuditAction;
import in.pragati.repo.AuditEventRepository;
import in.pragati.security.AuthUser;

/** Human-readable activity trail. Never stores passwords, tokens or PII beyond names. */
@Service
public class AuditService {

    private final AuditEventRepository repo;
    private final ObjectMapper json;

    public AuditService(AuditEventRepository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    public void log(AuthUser actor, AuditAction action, String targetType, String targetId,
                    String summary, Map<String, Object> details) {
        AuditEvent e = new AuditEvent();
        e.actorUserId = actor != null ? actor.id() : null;
        e.actorName = actor != null ? actor.name() : "System";
        e.action = action;
        e.targetType = targetType;
        e.targetId = targetId;
        e.summary = summary;
        e.detailsJson = write(details);
        repo.save(e);
    }

    public void logSystem(AuditAction action, String targetType, String targetId,
                          String summary, Map<String, Object> details) {
        log(null, action, targetType, targetId, summary, details);
    }

    public Page<AuditEvent> page(int page, int size, String actionFilter) {
        PageRequest pr = PageRequest.of(page, size);
        if (actionFilter == null || actionFilter.isBlank()) {
            return repo.findAllByOrderByAtDesc(pr);
        }
        return repo.findAllByOrderByAtDesc(pr);
    }

    private String write(Map<String, Object> details) {
        if (details == null || details.isEmpty()) return null;
        try {
            return json.writeValueAsString(details);
        } catch (Exception e) {
            return null;
        }
    }
}

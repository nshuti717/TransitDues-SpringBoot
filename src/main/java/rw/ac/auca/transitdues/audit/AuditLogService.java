package rw.ac.auca.transitdues.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.config.AuthenticatedUserResolver;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void record(String entityType, String entityId, String action, String details) {
        record(entityType, entityId, action, details, null);
    }

    /**
     * Same as {@link #record(String, String, String, String)}, but accepts a
     * performedBy fallback for actions with no authenticated principal (e.g.
     * operator self-registration), so the entry still names the person involved
     * instead of being left blank.
     */
    public void record(String entityType, String entityId, String action, String details, String performedByFallback) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String performedBy = AuthenticatedUserResolver.resolveAuditIdentity(authentication, performedByFallback);
        AuditLog auditLog = new AuditLog(null, entityType, entityId, action, performedBy, LocalDateTime.now(), details);
        auditLogRepository.save(auditLog);
    }
}

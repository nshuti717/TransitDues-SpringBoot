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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String performedBy = AuthenticatedUserResolver.resolveAuditIdentity(authentication);
        AuditLog auditLog = new AuditLog(null, entityType, entityId, action, performedBy, LocalDateTime.now(), details);
        auditLogRepository.save(auditLog);
    }
}

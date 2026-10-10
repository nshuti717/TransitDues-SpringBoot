package rw.ac.auca.transitdues.webcontroller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import rw.ac.auca.transitdues.audit.AuditLogRepository;
import rw.ac.auca.transitdues.audit.PaymentEventLogRepository;

@Controller
@RequestMapping("/web/audit-log")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogWebController {

    private final AuditLogRepository auditLogRepository;
    private final PaymentEventLogRepository paymentEventLogRepository;

    @GetMapping("")
    public String listAuditLogs(Model model) {
        model.addAttribute("logs", auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp")));
        model.addAttribute("paymentEvents",
                paymentEventLogRepository.findAll(Sort.by(Sort.Direction.DESC, "occurredAt")));
        return "audit-log";
    }
}

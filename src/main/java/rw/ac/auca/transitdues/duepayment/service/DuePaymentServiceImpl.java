package rw.ac.auca.transitdues.duepayment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.config.AuthenticatedUserResolver;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.repository.DuePaymentRepository;
import rw.ac.auca.transitdues.exception.DuePaymentNotFoundException;
import rw.ac.auca.transitdues.exception.OperatorNotFoundException;
import rw.ac.auca.transitdues.messaging.DuePaymentEvent;
import rw.ac.auca.transitdues.messaging.DuePaymentEventPublisher;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.repository.OperatorRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DuePaymentServiceImpl implements DuePaymentService {

    private final DuePaymentRepository duePaymentRepository;
    private final OperatorRepository operatorRepository;
    private final AuditLogService auditLogService;
    private final DuePaymentEventPublisher duePaymentEventPublisher;

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public DuePayment createDuePayment(DuePayment duePayment) {
        UUID operatorId = duePayment.getOperator().getId();
        Operator operator = operatorRepository.findById(operatorId)
                .orElseThrow(() -> new OperatorNotFoundException("Operator not found with id: " + operatorId));

        duePayment.setOperator(operator);
        DuePayment savedDuePayment = duePaymentRepository.save(duePayment);
        auditLogService.record("DuePayment", savedDuePayment.getId().toString(), "CREATE",
                savedDuePayment.getAmount() + " - " + savedDuePayment.getStatus());
        duePaymentEventPublisher.publish(toEvent(savedDuePayment, DuePaymentEvent.CREATED));
        return savedDuePayment;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public DuePayment updateDuePayment(UUID id, DuePayment duePayment) {
        DuePayment existingDuePayment = findDuePaymentById(id);
        existingDuePayment.setAmount(duePayment.getAmount());
        existingDuePayment.setType(duePayment.getType());
        existingDuePayment.setDatePaid(duePayment.getDatePaid());
        existingDuePayment.setStatus(duePayment.getStatus());
        existingDuePayment.setOperator(duePayment.getOperator());
        DuePayment savedDuePayment = duePaymentRepository.save(existingDuePayment);
        auditLogService.record("DuePayment", savedDuePayment.getId().toString(), "UPDATE",
                savedDuePayment.getAmount() + " - " + savedDuePayment.getStatus());
        duePaymentEventPublisher.publish(toEvent(savedDuePayment, DuePaymentEvent.UPDATED));
        return savedDuePayment;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public void deleteDuePayment(UUID id) {
        DuePayment duePayment = findDuePaymentById(id);
        duePaymentRepository.delete(duePayment);
        auditLogService.record("DuePayment", duePayment.getId().toString(), "DELETE",
                duePayment.getAmount() + " - " + duePayment.getStatus());
    }

    @Override
    public DuePayment findDuePaymentById(UUID id) {
        return duePaymentRepository.findById(id)
                .orElseThrow(() -> new DuePaymentNotFoundException("DuePayment not found with id: " + id));
    }

    @Override
    public List<DuePayment> findAllDuePayments() {
        return duePaymentRepository.findAll();
    }

    private DuePaymentEvent toEvent(DuePayment duePayment, String eventType) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String performedBy = AuthenticatedUserResolver.resolveDisplayName(authentication);
        Operator operator = duePayment.getOperator();
        return new DuePaymentEvent(eventType, duePayment.getId().toString(), operator.getId().toString(),
                operator.getFullName(), duePayment.getAmount(), duePayment.getStatus(), performedBy,
                LocalDateTime.now());
    }
}

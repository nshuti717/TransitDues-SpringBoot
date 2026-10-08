package rw.ac.auca.transitdues.duepayment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.config.AuthenticatedUserResolver;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.DuePaymentStatus;
import rw.ac.auca.transitdues.duepayment.domain.PaymentType;
import rw.ac.auca.transitdues.duepayment.repository.DuePaymentRepository;
import rw.ac.auca.transitdues.exception.DuePaymentNotFoundException;
import rw.ac.auca.transitdues.exception.DuplicateDuePaymentException;
import rw.ac.auca.transitdues.exception.OperatorNotFoundException;
import rw.ac.auca.transitdues.exception.StageNotFoundException;
import rw.ac.auca.transitdues.messaging.DuePaymentEvent;
import rw.ac.auca.transitdues.messaging.DuePaymentEventPublisher;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.repository.OperatorRepository;
import rw.ac.auca.transitdues.stage.service.StageService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DuePaymentServiceImpl implements DuePaymentService {

    private static final String DUPLICATE_DUE_MESSAGE = "This due is already issued for that operator and date.";

    private final DuePaymentRepository duePaymentRepository;
    private final OperatorRepository operatorRepository;
    private final StageService stageService;
    private final AuditLogService auditLogService;
    private final DuePaymentEventPublisher duePaymentEventPublisher;

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public DuePayment createDuePayment(DuePayment duePayment) {
        UUID operatorId = duePayment.getOperator().getId();
        Operator operator = operatorRepository.findById(operatorId)
                .orElseThrow(() -> new OperatorNotFoundException("Operator not found with id: " + operatorId));

        if (duePaymentRepository.existsByOperatorIdAndTypeAndDueDate(operatorId, duePayment.getType(),
                duePayment.getDueDate())) {
            throw new DuplicateDuePaymentException(DUPLICATE_DUE_MESSAGE);
        }

        duePayment.setOperator(operator);
        if (duePayment.getStatus() == null) {
            duePayment.setStatus(DuePaymentStatus.PENDING);
        }
        if (duePayment.getIssuedBy() == null || duePayment.getIssuedBy().isBlank()) {
            duePayment.setIssuedBy(currentIssuedBy());
        }

        DuePayment savedDuePayment = save(duePayment);
        auditLogService.record("DuePayment", savedDuePayment.getId().toString(), "CREATE",
                describe(savedDuePayment));
        duePaymentEventPublisher.publish(toEvent(savedDuePayment, DuePaymentEvent.CREATED));
        return savedDuePayment;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public BulkIssueResult bulkIssueDuePayments(PaymentType type, BigDecimal amount, LocalDate dueDate, UUID stageId) {
        List<Operator> operators;
        if (stageId == null) {
            operators = operatorRepository.findAll();
        } else {
            stageService.findStageById(stageId);
            operators = operatorRepository.findByStageId(stageId);
        }

        String issuedBy = currentIssuedBy();
        int issuedCount = 0;
        int skippedCount = 0;

        for (Operator operator : operators) {
            if (duePaymentRepository.existsByOperatorIdAndTypeAndDueDate(operator.getId(), type, dueDate)) {
                skippedCount++;
                continue;
            }

            DuePayment duePayment = new DuePayment();
            duePayment.setOperator(operator);
            duePayment.setType(type);
            duePayment.setAmount(amount);
            duePayment.setDueDate(dueDate);
            duePayment.setStatus(DuePaymentStatus.PENDING);
            duePayment.setIssuedBy(issuedBy);

            DuePayment savedDuePayment = save(duePayment);
            auditLogService.record("DuePayment", savedDuePayment.getId().toString(), "CREATE",
                    describe(savedDuePayment));
            duePaymentEventPublisher.publish(toEvent(savedDuePayment, DuePaymentEvent.CREATED));
            issuedCount++;
        }

        return new BulkIssueResult(issuedCount, skippedCount);
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public DuePayment updateDuePayment(UUID id, DuePayment duePayment) {
        DuePayment existingDuePayment = findDuePaymentById(id);

        if (duePaymentRepository.existsByOperatorIdAndTypeAndDueDateAndIdNot(duePayment.getOperator().getId(),
                duePayment.getType(), duePayment.getDueDate(), id)) {
            throw new DuplicateDuePaymentException(DUPLICATE_DUE_MESSAGE);
        }

        existingDuePayment.setAmount(duePayment.getAmount());
        existingDuePayment.setType(duePayment.getType());
        existingDuePayment.setDueDate(duePayment.getDueDate());
        existingDuePayment.setStatus(duePayment.getStatus());
        existingDuePayment.setReference(duePayment.getReference());
        existingDuePayment.setPaidAt(duePayment.getPaidAt());
        existingDuePayment.setPaymentMethod(duePayment.getPaymentMethod());
        existingDuePayment.setOperator(duePayment.getOperator());

        DuePayment savedDuePayment = save(existingDuePayment);
        auditLogService.record("DuePayment", savedDuePayment.getId().toString(), "UPDATE",
                describe(savedDuePayment));
        duePaymentEventPublisher.publish(toEvent(savedDuePayment, DuePaymentEvent.UPDATED));
        return savedDuePayment;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public void deleteDuePayment(UUID id) {
        DuePayment duePayment = findDuePaymentById(id);
        duePaymentRepository.delete(duePayment);
        auditLogService.record("DuePayment", duePayment.getId().toString(), "DELETE", describe(duePayment));
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

    /**
     * The existsBy... checks above catch almost every duplicate, but a
     * concurrent request can still slip past them before either one commits.
     * The database's unique constraint on (operator, type, due_date) is the
     * backstop for that race, translated to the same friendly message - but
     * only that constraint; any other integrity violation is a real bug and
     * should surface as-is rather than being misreported as a duplicate.
     */
    private DuePayment save(DuePayment duePayment) {
        try {
            return duePaymentRepository.save(duePayment);
        } catch (DataIntegrityViolationException ex) {
            String message = ex.getMostSpecificCause().getMessage();
            if (message != null && message.contains("uk_due_payment_operator_type_due_date")) {
                throw new DuplicateDuePaymentException(DUPLICATE_DUE_MESSAGE);
            }
            throw ex;
        }
    }

    private String describe(DuePayment duePayment) {
        return duePayment.getAmount() + " - " + duePayment.getStatus();
    }

    private String currentIssuedBy() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return AuthenticatedUserResolver.resolveDisplayName(authentication);
    }

    private DuePaymentEvent toEvent(DuePayment duePayment, String eventType) {
        String performedBy = currentIssuedBy();
        Operator operator = duePayment.getOperator();
        return new DuePaymentEvent(eventType, duePayment.getId().toString(), operator.getId().toString(),
                operator.getFullName(), duePayment.getAmount(), duePayment.getStatus().name(), performedBy,
                LocalDateTime.now());
    }
}

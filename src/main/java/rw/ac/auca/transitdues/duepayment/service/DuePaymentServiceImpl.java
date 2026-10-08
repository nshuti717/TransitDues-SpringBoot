package rw.ac.auca.transitdues.duepayment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.config.AuthenticatedUserResolver;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.DuePaymentStatus;
import rw.ac.auca.transitdues.duepayment.domain.PaymentMethod;
import rw.ac.auca.transitdues.duepayment.domain.PaymentType;
import rw.ac.auca.transitdues.duepayment.repository.DuePaymentRepository;
import rw.ac.auca.transitdues.exception.DuePaymentNotFoundException;
import rw.ac.auca.transitdues.exception.DuplicateDuePaymentException;
import rw.ac.auca.transitdues.exception.InvalidPaymentStateException;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class DuePaymentServiceImpl implements DuePaymentService {

    private static final String DUPLICATE_DUE_MESSAGE = "This due is already issued for that operator and date.";
    private static final String NOT_OWNER_MESSAGE = "You cannot act on another operator's due payment.";
    private static final Set<DuePaymentStatus> PAYABLE_STATUSES =
            EnumSet.of(DuePaymentStatus.PENDING, DuePaymentStatus.OVERDUE, DuePaymentStatus.FAILED);

    private final DuePaymentRepository duePaymentRepository;
    private final OperatorRepository operatorRepository;
    private final StageService stageService;
    private final AuditLogService auditLogService;
    private final DuePaymentEventPublisher duePaymentEventPublisher;

    /**
     * Simulated payment-gateway failure rate (0.0-1.0), since no real payment
     * provider is integrated. Defaults to never failing; tests can override
     * {@code app.payments.simulated-failure-rate} to exercise the FAILED path.
     */
    @Value("${app.payments.simulated-failure-rate:0.0}")
    private double simulatedFailureRate;

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

    @Override
    public List<DuePayment> findDuePaymentsByOperator(UUID operatorId) {
        return duePaymentRepository.findByOperatorIdOrderByDueDateDesc(operatorId);
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public DuePayment initiateOnlinePayment(UUID duePaymentId, Operator payingOperator) {
        DuePayment duePayment = requirePayable(duePaymentId, payingOperator);

        duePayment.setStatus(DuePaymentStatus.SUBMITTED);
        duePayment.setPaymentMethod(PaymentMethod.ONLINE);
        duePayment.setSubmittedAt(LocalDate.now());

        DuePayment saved = save(duePayment);
        auditLogService.record("DuePayment", saved.getId().toString(), "PAY_SUBMIT", describe(saved));
        duePaymentEventPublisher.publish(toEvent(saved, DuePaymentEvent.PAYMENT_SUBMITTED));
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public DuePayment confirmOnlinePayment(UUID duePaymentId, Operator payingOperator) {
        DuePayment duePayment = findDuePaymentById(duePaymentId);
        requireOwnedBy(duePayment, payingOperator);
        if (duePayment.getStatus() != DuePaymentStatus.SUBMITTED) {
            throw new InvalidPaymentStateException("There is no submitted online payment to confirm for this due.");
        }

        boolean succeeded = ThreadLocalRandom.current().nextDouble() >= simulatedFailureRate;
        String action;
        String eventType;
        if (succeeded) {
            duePayment.setStatus(DuePaymentStatus.PAID);
            duePayment.setPaidAt(LocalDate.now());
            duePayment.setReference("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            action = "PAY_CONFIRM";
            eventType = DuePaymentEvent.PAYMENT_CONFIRMED;
        } else {
            duePayment.setStatus(DuePaymentStatus.FAILED);
            action = "PAY_FAILED";
            eventType = DuePaymentEvent.PAYMENT_FAILED;
        }

        DuePayment saved = save(duePayment);
        auditLogService.record("DuePayment", saved.getId().toString(), action, describe(saved));
        duePaymentEventPublisher.publish(toEvent(saved, eventType));
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public DuePayment cancelOnlinePayment(UUID duePaymentId, Operator payingOperator) {
        DuePayment duePayment = findDuePaymentById(duePaymentId);
        requireOwnedBy(duePayment, payingOperator);
        if (duePayment.getStatus() != DuePaymentStatus.SUBMITTED) {
            throw new InvalidPaymentStateException("There is no submitted online payment to cancel for this due.");
        }

        duePayment.setStatus(DuePaymentStatus.PENDING);
        duePayment.setPaymentMethod(null);
        duePayment.setSubmittedAt(null);

        DuePayment saved = save(duePayment);
        auditLogService.record("DuePayment", saved.getId().toString(), "PAY_CANCEL", describe(saved));
        duePaymentEventPublisher.publish(toEvent(saved, DuePaymentEvent.UPDATED));
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public DuePayment requestCashPayment(UUID duePaymentId, Operator payingOperator) {
        DuePayment duePayment = requirePayable(duePaymentId, payingOperator);

        duePayment.setStatus(DuePaymentStatus.CASH_PENDING);
        duePayment.setPaymentMethod(PaymentMethod.CASH);
        duePayment.setSubmittedAt(LocalDate.now());

        DuePayment saved = save(duePayment);
        auditLogService.record("DuePayment", saved.getId().toString(), "CASH_REQUEST", describe(saved));
        duePaymentEventPublisher.publish(toEvent(saved, DuePaymentEvent.PAYMENT_CASH_REQUESTED));
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public DuePayment confirmCashPayment(UUID duePaymentId) {
        DuePayment duePayment = findDuePaymentById(duePaymentId);
        if (duePayment.getStatus() != DuePaymentStatus.CASH_PENDING) {
            throw new InvalidPaymentStateException("This due has no cash payment awaiting confirmation.");
        }

        duePayment.setStatus(DuePaymentStatus.PAID);
        duePayment.setPaidAt(LocalDate.now());
        duePayment.setReference("CASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        duePayment.setConfirmedBy(currentIssuedBy());

        DuePayment saved = save(duePayment);
        auditLogService.record("DuePayment", saved.getId().toString(), "CASH_CONFIRM", describe(saved));
        duePaymentEventPublisher.publish(toEvent(saved, DuePaymentEvent.PAYMENT_CONFIRMED));
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public DuePayment rejectCashPayment(UUID duePaymentId) {
        DuePayment duePayment = findDuePaymentById(duePaymentId);
        if (duePayment.getStatus() != DuePaymentStatus.CASH_PENDING) {
            throw new InvalidPaymentStateException("This due has no cash payment awaiting confirmation.");
        }

        duePayment.setStatus(DuePaymentStatus.PENDING);
        duePayment.setPaymentMethod(null);
        duePayment.setSubmittedAt(null);

        DuePayment saved = save(duePayment);
        auditLogService.record("DuePayment", saved.getId().toString(), "CASH_REJECT", describe(saved));
        duePaymentEventPublisher.publish(toEvent(saved, DuePaymentEvent.UPDATED));
        return saved;
    }

    private DuePayment requirePayable(UUID duePaymentId, Operator payingOperator) {
        DuePayment duePayment = findDuePaymentById(duePaymentId);
        requireOwnedBy(duePayment, payingOperator);
        if (!PAYABLE_STATUSES.contains(duePayment.getStatus())) {
            throw new InvalidPaymentStateException(
                    "This due cannot be paid right now (current status: " + duePayment.getStatus() + ").");
        }
        return duePayment;
    }

    private void requireOwnedBy(DuePayment duePayment, Operator payingOperator) {
        if (payingOperator == null || !duePayment.getOperator().getId().equals(payingOperator.getId())) {
            throw new AccessDeniedException(NOT_OWNER_MESSAGE);
        }
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
                LocalDateTime.now(), duePayment.getReference(),
                duePayment.getPaymentMethod() == null ? null : duePayment.getPaymentMethod().name());
    }
}

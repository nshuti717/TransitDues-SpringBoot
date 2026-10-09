package rw.ac.auca.transitdues.operator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.config.AuthenticatedUserResolver;
import rw.ac.auca.transitdues.email.EmailEventPublisher;
import rw.ac.auca.transitdues.exception.DuplicatePlateException;
import rw.ac.auca.transitdues.exception.OperatorNotEligibleException;
import rw.ac.auca.transitdues.exception.OperatorNotFoundException;
import rw.ac.auca.transitdues.exception.SelfActionNotAllowedException;
import rw.ac.auca.transitdues.exception.StageCapacityExceededException;
import rw.ac.auca.transitdues.exception.StageNotFoundException;
import rw.ac.auca.transitdues.operator.domain.ApprovalStatus;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.repository.OperatorRepository;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.repository.StageRepository;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperatorServiceImpl implements OperatorService {

    private static final String DUPLICATE_PLATE_MESSAGE = "This plate number is already registered.";

    /**
     * A stage slot is considered free again once an operator who held it is
     * REJECTED or DEACTIVATED - every other status (PENDING_APPROVAL, ACTIVE,
     * SUSPENDED) still occupies the slot it was assigned.
     */
    private static final Set<ApprovalStatus> RELEASES_STAGE_SLOT =
            EnumSet.of(ApprovalStatus.REJECTED, ApprovalStatus.DEACTIVATED);

    private final OperatorRepository operatorRepository;
    private final StageRepository stageRepository;
    private final AuditLogService auditLogService;
    private final UserAccountRepository userAccountRepository;
    private final EmailEventPublisher emailEventPublisher;

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Operator createOperator(Operator operator, String performedByOverride) {
        UUID stageId = operator.getStage().getId();
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new StageNotFoundException("Stage not found with id: " + stageId));

        int currentOperatorCount = operatorRepository.countByStageIdAndApprovalStatusNotIn(stageId, RELEASES_STAGE_SLOT);
        if (currentOperatorCount >= stage.getCapacity()) {
            throw new StageCapacityExceededException(
                    "Stage '" + stage.getName() + "' has reached its capacity of " + stage.getCapacity());
        }

        String normalizedPlate = normalizePlateNumber(operator.getPlateNumber());
        if (operatorRepository.existsByPlateNumberIgnoreCase(normalizedPlate)) {
            throw new DuplicatePlateException(DUPLICATE_PLATE_MESSAGE);
        }

        operator.setPlateNumber(normalizedPlate);
        operator.setStage(stage);
        Operator savedOperator = saveOperator(operator);
        auditLogService.record("Operator", savedOperator.getId().toString(), "CREATE", savedOperator.getFullName(),
                performedByOverride);
        return savedOperator;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Operator updateOperator(UUID id, Operator operator) {
        Operator existingOperator = findOperatorById(id);

        String normalizedPlate = normalizePlateNumber(operator.getPlateNumber());
        if (operatorRepository.existsByPlateNumberIgnoreCaseAndIdNot(normalizedPlate, id)) {
            throw new DuplicatePlateException(DUPLICATE_PLATE_MESSAGE);
        }

        existingOperator.setFullName(operator.getFullName());
        existingOperator.setPhoneNumber(operator.getPhoneNumber());
        existingOperator.setPlateNumber(normalizedPlate);
        existingOperator.setStage(operator.getStage());
        Operator savedOperator = saveOperator(existingOperator);
        auditLogService.record("Operator", savedOperator.getId().toString(), "UPDATE", savedOperator.getFullName());
        return savedOperator;
    }

    /**
     * The explicit existsBy... checks above catch almost every duplicate, but a
     * concurrent request can still slip past both checks before either one
     * commits. The database's unique constraint on plate_number is the backstop
     * for that race, and its violation is translated to the same friendly
     * message rather than surfacing a raw SQL error.
     */
    private Operator saveOperator(Operator operator) {
        try {
            return operatorRepository.save(operator);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicatePlateException(DUPLICATE_PLATE_MESSAGE);
        }
    }

    private String normalizePlateNumber(String plateNumber) {
        if (plateNumber == null) {
            return null;
        }
        return plateNumber.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public void deleteOperator(UUID id) {
        Operator operator = findOperatorById(id);
        operatorRepository.delete(operator);
        auditLogService.record("Operator", operator.getId().toString(), "DELETE", operator.getFullName());
    }

    @Override
    public Operator findOperatorById(UUID id) {
        return operatorRepository.findById(id)
                .orElseThrow(() -> new OperatorNotFoundException("Operator not found with id: " + id));
    }

    @Override
    public List<Operator> findAllOperators() {
        return operatorRepository.findAll();
    }

    @Override
    public List<Operator> findActiveListOperators() {
        return operatorRepository.findByApprovalStatusNotIn(EnumSet.of(ApprovalStatus.DEACTIVATED));
    }

    @Override
    public List<Operator> findPendingApprovalOperators() {
        return operatorRepository.findByApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
    }

    @Override
    public List<Operator> findEligibleActiveOperators() {
        return operatorRepository.findByApprovalStatus(ApprovalStatus.ACTIVE);
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Operator approveOperator(UUID id, UUID stageId, String reason) {
        Operator operator = findOperatorById(id);
        requireNotDeactivated(operator);

        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new StageNotFoundException("Stage not found with id: " + stageId));

        if (!stage.getId().equals(operator.getStage().getId())) {
            int occupied = operatorRepository.countByStageIdAndApprovalStatusNotIn(stageId, RELEASES_STAGE_SLOT);
            if (occupied >= stage.getCapacity()) {
                throw new StageCapacityExceededException(
                        "Stage '" + stage.getName() + "' has reached its capacity of " + stage.getCapacity());
            }
        }

        operator.setStage(stage);
        recordApprovalAction(operator, ApprovalStatus.ACTIVE, reason);
        Operator saved = operatorRepository.save(operator);
        auditLogService.record("Operator", saved.getId().toString(), "APPROVE",
                "Approved and assigned to stage " + stage.getName()
                        + (reason != null && !reason.isBlank() ? " (" + reason + ")" : ""));
        notifyOperator(saved, "Your TransitDues operator application was approved",
                "Good news - your operator application has been approved. You are now assigned to stage '"
                        + stage.getName() + "' and can see and pay your dues at the operator portal.");
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Operator rejectOperator(UUID id, String reason) {
        Operator operator = findOperatorById(id);
        requireNotDeactivated(operator);

        recordApprovalAction(operator, ApprovalStatus.REJECTED, reason);
        Operator saved = operatorRepository.save(operator);
        auditLogService.record("Operator", saved.getId().toString(), "REJECT", describeReason(reason));
        notifyOperator(saved, "Your TransitDues operator application was not approved",
                "Your operator application was not approved."
                        + (reason != null && !reason.isBlank() ? " Reason: " + reason + "." : "")
                        + " Contact a Finance Officer or Administrator if you have questions.");
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Operator suspendOperator(UUID id, String reason) {
        Operator operator = findOperatorById(id);
        requireNotDeactivated(operator);

        recordApprovalAction(operator, ApprovalStatus.SUSPENDED, reason);
        Operator saved = operatorRepository.save(operator);
        auditLogService.record("Operator", saved.getId().toString(), "SUSPEND", describeReason(reason));
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Operator deactivateOperator(UUID id, String reason, String requesterIdentifier) {
        Operator operator = findOperatorById(id);
        if (operator.getApprovalStatus() == ApprovalStatus.DEACTIVATED) {
            throw new OperatorNotEligibleException("This operator has already been deactivated.");
        }

        Optional<UserAccount> linkedAccount = userAccountRepository.findByOperatorId(id);
        if (requesterIdentifier != null && linkedAccount.isPresent()
                && requesterIdentifier.equalsIgnoreCase(linkedAccount.get().getEmail())) {
            throw new SelfActionNotAllowedException(
                    "You cannot deactivate the operator account linked to your own login.");
        }

        operator.setApprovalStatus(ApprovalStatus.DEACTIVATED);
        operator.setApprovalActionBy(requesterIdentifier);
        operator.setApprovalActionAt(LocalDateTime.now());
        operator.setApprovalReason(reason);
        Operator saved = operatorRepository.save(operator);

        // Disabling the login (rather than deleting the UserAccount row) is what
        // blocks sign-in afterwards - see CustomUserDetailsService/OAuth2UserRoleMapper
        // - while leaving every due/payment/audit record this operator is tied to
        // completely untouched.
        linkedAccount.ifPresent(account -> {
            account.setEnabled(false);
            userAccountRepository.save(account);
        });

        auditLogService.record("Operator", saved.getId().toString(), "DEACTIVATE", describeReason(reason));
        return saved;
    }

    /**
     * Sends an approval/rejection outcome email through the same real
     * RabbitMQ-backed email pipeline OTP/password-reset emails already use
     * (EmailEventPublisher -&gt; EmailSendConsumer -&gt; Mailpit/real SMTP) - never
     * logged as a "simulated" notification. Silently does nothing if this
     * operator has no linked login account to email (e.g. admin-created with
     * no email), since there is nowhere to send it.
     */
    private void notifyOperator(Operator operator, String subject, String body) {
        userAccountRepository.findByOperatorId(operator.getId())
                .ifPresent(account -> emailEventPublisher.publish(account.getEmail(), subject, body));
    }

    private void requireNotDeactivated(Operator operator) {
        if (operator.getApprovalStatus() == ApprovalStatus.DEACTIVATED) {
            throw new OperatorNotEligibleException("This operator has been deactivated and can no longer be reviewed.");
        }
    }

    private void recordApprovalAction(Operator operator, ApprovalStatus newStatus, String reason) {
        operator.setApprovalStatus(newStatus);
        operator.setApprovalActionBy(currentIssuedBy());
        operator.setApprovalActionAt(LocalDateTime.now());
        operator.setApprovalReason(reason);
    }

    private String describeReason(String reason) {
        return reason != null && !reason.isBlank() ? reason : "No reason given";
    }

    private String currentIssuedBy() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return AuthenticatedUserResolver.resolveDisplayName(authentication);
    }
}

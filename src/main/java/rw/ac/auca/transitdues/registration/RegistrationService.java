package rw.ac.auca.transitdues.registration;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.exception.DuplicateEmailException;
import rw.ac.auca.transitdues.exception.DuplicatePhoneException;
import rw.ac.auca.transitdues.exception.InvalidRegistrationException;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.service.OperatorService;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.user.domain.Role;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;
import rw.ac.auca.transitdues.user.service.UserAccountService;

import java.util.Locale;
import java.util.Set;

/**
 * Owns operator self-registration at /register and the admin-only path of
 * creating an Operator with an optional login account. Both reuse
 * OperatorService.createOperator for the stage-exists, stage-capacity and
 * duplicate-plate checks rather than duplicating them, and route every
 * UserAccount write through UserAccountService so the one-role-per-account
 * rule is enforced in a single place.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final OperatorService operatorService;
    private final UserAccountService userAccountService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates an Operator and its OPERATOR-only login account in one transaction:
     * if the capacity or duplicate-plate check inside operatorService.createOperator
     * fails, nothing is persisted for either row.
     */
    @Transactional
    public UserAccount registerOperator(RegisterForm form) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new InvalidRegistrationException("Passwords do not match.");
        }

        String email = normalizeEmail(form.getEmail());
        if (userAccountRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new DuplicateEmailException("An account with this email already exists.");
        }
        if (userAccountRepository.findByOperatorPhoneNumber(form.getPhoneNumber()).isPresent()) {
            throw new DuplicatePhoneException("An account with this phone number already exists.");
        }

        Stage stageRef = new Stage();
        stageRef.setId(form.getStageId());

        Operator operator = new Operator();
        operator.setFullName(form.getFullName());
        operator.setPhoneNumber(form.getPhoneNumber());
        operator.setPlateNumber(form.getPlateNumber());
        operator.setStage(stageRef);

        // There is no authenticated principal during self-registration, so the
        // new account's own email is passed through as the audit performer.
        Operator savedOperator = operatorService.createOperator(operator, email);

        UserAccount account = new UserAccount();
        account.setFullName(form.getFullName());
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        account.setEnabled(true);
        account.setRoles(Set.of(Role.OPERATOR));
        account.setOperator(savedOperator);
        UserAccount savedAccount = userAccountService.save(account);

        auditLogService.record("UserAccount", savedAccount.getId().toString(), "CREATE",
                "Operator self-registration", email);
        return savedAccount;
    }

    /**
     * Admin-only path: create an Operator, and optionally a login account for it,
     * in one transaction. Used by the Operator create form's optional email/
     * initial password fields.
     */
    @Transactional
    public Operator createOperatorWithOptionalLogin(Operator operator, String email, String initialPassword) {
        Operator savedOperator = operatorService.createOperator(operator);
        if (email != null && !email.isBlank()) {
            createLoginForOperator(savedOperator, email, initialPassword);
        }
        return savedOperator;
    }

    private UserAccount createLoginForOperator(Operator operator, String email, String initialPassword) {
        if (initialPassword == null || initialPassword.length() < 8) {
            throw new InvalidRegistrationException("Initial password must be at least 8 characters.");
        }

        String normalizedEmail = normalizeEmail(email);
        if (userAccountRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new DuplicateEmailException("An account with this email already exists.");
        }

        UserAccount account = new UserAccount();
        account.setFullName(operator.getFullName());
        account.setEmail(normalizedEmail);
        account.setPasswordHash(passwordEncoder.encode(initialPassword));
        account.setEnabled(true);
        account.setRoles(Set.of(Role.OPERATOR));
        account.setOperator(operator);
        UserAccount savedAccount = userAccountService.save(account);
        auditLogService.record("UserAccount", savedAccount.getId().toString(), "CREATE",
                "Login account for operator " + operator.getFullName());
        return savedAccount;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

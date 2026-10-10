package rw.ac.auca.transitdues.registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.exception.DuplicateEmailException;
import rw.ac.auca.transitdues.exception.StageCapacityExceededException;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.service.OperatorService;
import rw.ac.auca.transitdues.user.domain.AccountStatus;
import rw.ac.auca.transitdues.user.domain.Role;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;
import rw.ac.auca.transitdues.user.service.UserAccountService;
import rw.ac.auca.transitdues.verification.AccountVerificationService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private OperatorService operatorService;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountVerificationService accountVerificationService;

    @InjectMocks
    private RegistrationService registrationService;

    @Test
    void registersOperatorSuccessfully() {
        RegisterForm form = form();
        when(userAccountRepository.findByEmailIgnoreCase("new.operator@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.findByOperatorPhoneNumber("0788111222")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");

        Operator savedOperator = new Operator();
        savedOperator.setId(UUID.randomUUID());
        savedOperator.setFullName(form.getFullName());
        when(operatorService.createOperator(any(Operator.class), eq("new.operator@example.com")))
                .thenReturn(savedOperator);

        when(userAccountService.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });

        UserAccount result = registrationService.registerOperator(form);

        assertEquals("new.operator@example.com", result.getEmail());
        assertEquals("hashed-password", result.getPasswordHash());
        assertTrue(result.getRoles().contains(Role.OPERATOR));
        assertEquals(1, result.getRoles().size());
        assertEquals(savedOperator, result.getOperator());
        assertEquals(AccountStatus.PENDING_VERIFICATION, result.getStatus());
        verify(auditLogService).record(eq("UserAccount"), anyString(), eq("CREATE"), anyString(),
                eq("new.operator@example.com"));
        verify(accountVerificationService).sendVerificationCode("new.operator@example.com");
    }

    @Test
    void duplicateEmailIsRejectedBeforeTouchingOperator() {
        RegisterForm form = form();
        when(userAccountRepository.findByEmailIgnoreCase("new.operator@example.com"))
                .thenReturn(Optional.of(new UserAccount()));

        assertThrows(DuplicateEmailException.class, () -> registrationService.registerOperator(form));

        verifyNoInteractions(operatorService);
    }

    @Test
    void fullStagePropagatesAsStageCapacityExceeded() {
        RegisterForm form = form();
        when(userAccountRepository.findByEmailIgnoreCase("new.operator@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.findByOperatorPhoneNumber("0788111222")).thenReturn(Optional.empty());
        when(operatorService.createOperator(any(Operator.class), eq("new.operator@example.com")))
                .thenThrow(new StageCapacityExceededException("Stage 'Remera' has reached its capacity of 2"));

        assertThrows(StageCapacityExceededException.class, () -> registrationService.registerOperator(form));
    }

    private RegisterForm form() {
        RegisterForm form = new RegisterForm();
        form.setFullName("New Operator");
        form.setEmail("New.Operator@Example.com");
        form.setPhoneNumber("0788111222");
        form.setPlateNumber("RAB111A");
        form.setStageId(UUID.randomUUID());
        form.setPassword("password123");
        form.setConfirmPassword("password123");
        return form;
    }
}

package rw.ac.auca.transitdues.passwordreset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.email.EmailEventPublisher;
import rw.ac.auca.transitdues.otp.OtpPurpose;
import rw.ac.auca.transitdues.otp.OtpService;
import rw.ac.auca.transitdues.otp.OtpVerificationResult;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;
import rw.ac.auca.transitdues.user.service.UserAccountService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private OtpService otpService;

    @Mock
    private EmailEventPublisher emailEventPublisher;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userAccountRepository, userAccountService, otpService,
                emailEventPublisher, passwordEncoder, auditLogService);
    }

    @Test
    void requestResetSendsAnEmailWhenTheAccountExists() {
        UserAccount account = new UserAccount();
        when(userAccountRepository.findByEmailIgnoreCase("op@example.com")).thenReturn(Optional.of(account));
        when(otpService.generate("op@example.com", OtpPurpose.PASSWORD_RESET)).thenReturn("123456");

        service.requestReset(" Op@Example.com ");

        verify(emailEventPublisher).publish(eq("op@example.com"), anyString(), org.mockito.ArgumentMatchers.contains("123456"));
    }

    @Test
    void requestResetIsSilentWhenNoAccountExists_noEnumeration() {
        when(userAccountRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@example.com");

        verify(otpService, never()).generate(anyString(), any());
        verify(emailEventPublisher, never()).publish(anyString(), anyString(), anyString());
    }

    @Test
    void resetPasswordUpdatesThePasswordWhenOtpIsVerified() {
        UserAccount account = new UserAccount();
        account.setId(UUID.randomUUID());
        when(otpService.verify("op@example.com", OtpPurpose.PASSWORD_RESET, "123456"))
                .thenReturn(OtpVerificationResult.VERIFIED);
        when(userAccountRepository.findByEmailIgnoreCase("op@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("hashed");

        PasswordResetOutcome outcome = service.resetPassword("op@example.com", "123456", "NewPassword1");

        assertEquals(PasswordResetOutcome.SUCCESS, outcome);
        assertEquals("hashed", account.getPasswordHash());
        verify(userAccountService).save(account);
    }

    @Test
    void resetPasswordRejectsAnIncorrectCodeWithoutTouchingTheAccount() {
        when(otpService.verify("op@example.com", OtpPurpose.PASSWORD_RESET, "000000"))
                .thenReturn(OtpVerificationResult.INCORRECT);

        PasswordResetOutcome outcome = service.resetPassword("op@example.com", "000000", "NewPassword1");

        assertEquals(PasswordResetOutcome.INCORRECT_OR_EXPIRED, outcome);
        verify(userAccountService, never()).save(any());
    }

    @Test
    void resetPasswordRejectsOnceTooManyAttempts() {
        when(otpService.verify("op@example.com", OtpPurpose.PASSWORD_RESET, "000000"))
                .thenReturn(OtpVerificationResult.TOO_MANY_ATTEMPTS);

        PasswordResetOutcome outcome = service.resetPassword("op@example.com", "000000", "NewPassword1");

        assertEquals(PasswordResetOutcome.TOO_MANY_ATTEMPTS, outcome);
    }
}

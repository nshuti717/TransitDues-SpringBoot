package rw.ac.auca.transitdues.loginverification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.email.EmailEventPublisher;
import rw.ac.auca.transitdues.otp.OtpPurpose;
import rw.ac.auca.transitdues.otp.OtpService;
import rw.ac.auca.transitdues.otp.OtpVerificationResult;
import rw.ac.auca.transitdues.user.domain.AccountStatus;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginVerificationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private OtpService otpService;

    @Mock
    private EmailEventPublisher emailEventPublisher;

    @Mock
    private AuditLogService auditLogService;

    private LoginVerificationService service;

    @BeforeEach
    void setUp() {
        service = new LoginVerificationService(userAccountRepository, otpService, emailEventPublisher,
                auditLogService);
        ReflectionTestUtils.setField(service, "otpExpiryMinutes", 10L);
        ReflectionTestUtils.setField(service, "resendCooldownSeconds", 60L);
    }

    @Test
    void sendLoginCodeGeneratesAndEmailsACode() {
        when(otpService.generate("op@example.com", OtpPurpose.LOGIN_VERIFY)).thenReturn("555444");

        service.sendLoginCode(" Op@Example.com ");

        verify(emailEventPublisher).publish(eq("op@example.com"), eq("Your TransitDues sign-in code"),
                contains("555444"));
    }

    @Test
    void verifySucceedsForAnActiveEnabledAccount() {
        UserAccount account = new UserAccount();
        account.setId(UUID.randomUUID());
        account.setStatus(AccountStatus.ACTIVE);
        account.setEnabled(true);
        when(otpService.verify("op@example.com", OtpPurpose.LOGIN_VERIFY, "123456"))
                .thenReturn(OtpVerificationResult.VERIFIED);
        when(userAccountRepository.findByEmailIgnoreCase("op@example.com")).thenReturn(Optional.of(account));

        LoginVerificationOutcome outcome = service.verify("op@example.com", "123456");

        assertEquals(LoginVerificationOutcome.SUCCESS, outcome);
        verify(auditLogService).record(eq("UserAccount"), anyString(), eq("LOGIN_VERIFY"), anyString(),
                eq("op@example.com"));
    }

    @Test
    void verifyRejectsADisabledAccountEvenWithACorrectCode() {
        UserAccount account = new UserAccount();
        account.setStatus(AccountStatus.ACTIVE);
        account.setEnabled(false);
        when(otpService.verify("op@example.com", OtpPurpose.LOGIN_VERIFY, "123456"))
                .thenReturn(OtpVerificationResult.VERIFIED);
        when(userAccountRepository.findByEmailIgnoreCase("op@example.com")).thenReturn(Optional.of(account));

        LoginVerificationOutcome outcome = service.verify("op@example.com", "123456");

        assertEquals(LoginVerificationOutcome.INCORRECT_OR_EXPIRED, outcome);
    }

    @Test
    void verifyRejectsAnIncorrectCode() {
        when(otpService.verify("op@example.com", OtpPurpose.LOGIN_VERIFY, "000000"))
                .thenReturn(OtpVerificationResult.INCORRECT);

        LoginVerificationOutcome outcome = service.verify("op@example.com", "000000");

        assertEquals(LoginVerificationOutcome.INCORRECT_OR_EXPIRED, outcome);
    }

    @Test
    void verifyRejectsOnceTooManyAttempts() {
        when(otpService.verify("op@example.com", OtpPurpose.LOGIN_VERIFY, "123456"))
                .thenReturn(OtpVerificationResult.TOO_MANY_ATTEMPTS);

        LoginVerificationOutcome outcome = service.verify("op@example.com", "123456");

        assertEquals(LoginVerificationOutcome.TOO_MANY_ATTEMPTS, outcome);
    }

    @Test
    void resendSendsANewCodeWhenNoCooldownIsActive() {
        UserAccount account = new UserAccount();
        account.setStatus(AccountStatus.ACTIVE);
        account.setEnabled(true);
        when(userAccountRepository.findByEmailIgnoreCase("op@example.com")).thenReturn(Optional.of(account));
        when(otpService.secondsUntilResendAllowed("op@example.com", OtpPurpose.LOGIN_VERIFY, 60)).thenReturn(0L);
        when(otpService.generate("op@example.com", OtpPurpose.LOGIN_VERIFY)).thenReturn("111111");

        long secondsRemaining = service.resend("op@example.com");

        assertEquals(0, secondsRemaining);
        verify(emailEventPublisher).publish(eq("op@example.com"), anyString(), anyString());
    }

    @Test
    void resendRefusesWhileOnCooldown() {
        UserAccount account = new UserAccount();
        account.setStatus(AccountStatus.ACTIVE);
        account.setEnabled(true);
        when(userAccountRepository.findByEmailIgnoreCase("op@example.com")).thenReturn(Optional.of(account));
        when(otpService.secondsUntilResendAllowed("op@example.com", OtpPurpose.LOGIN_VERIFY, 60)).thenReturn(33L);

        long secondsRemaining = service.resend("op@example.com");

        assertEquals(33, secondsRemaining);
        verify(emailEventPublisher, never()).publish(anyString(), anyString(), anyString());
    }

    @Test
    void resendIsASilentNoOpForAnUnknownEmail_noEnumeration() {
        when(userAccountRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        long secondsRemaining = service.resend("nobody@example.com");

        assertEquals(0, secondsRemaining);
        verify(emailEventPublisher, never()).publish(anyString(), anyString(), anyString());
    }
}

package rw.ac.auca.transitdues.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.email.EmailEventPublisher;
import rw.ac.auca.transitdues.otp.OtpPurpose;
import rw.ac.auca.transitdues.otp.OtpService;
import rw.ac.auca.transitdues.otp.OtpVerificationResult;
import rw.ac.auca.transitdues.user.domain.AccountStatus;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;
import rw.ac.auca.transitdues.user.service.UserAccountService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountVerificationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private OtpService otpService;

    @Mock
    private EmailEventPublisher emailEventPublisher;

    @Mock
    private AuditLogService auditLogService;

    private AccountVerificationService service;

    @BeforeEach
    void setUp() {
        service = new AccountVerificationService(userAccountRepository, userAccountService, otpService,
                emailEventPublisher, auditLogService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "otpExpiryMinutes", 10L);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "resendCooldownSeconds", 60L);
    }

    @Test
    void sendVerificationCodeGeneratesAndEmailsACode() {
        when(otpService.generate("op@example.com", OtpPurpose.REGISTRATION_VERIFY)).thenReturn("654321");

        service.sendVerificationCode(" Op@Example.com ");

        verify(emailEventPublisher).publish(eq("op@example.com"), eq("Verify your TransitDues account"),
                contains("654321"));
    }

    @Test
    void verifyActivatesTheAccountWhenTheCodeIsCorrect() {
        UserAccount account = new UserAccount();
        account.setId(UUID.randomUUID());
        account.setStatus(AccountStatus.PENDING_VERIFICATION);
        when(otpService.verify("op@example.com", OtpPurpose.REGISTRATION_VERIFY, "123456"))
                .thenReturn(OtpVerificationResult.VERIFIED);
        when(userAccountRepository.findByEmailIgnoreCase("op@example.com")).thenReturn(Optional.of(account));

        AccountVerificationOutcome outcome = service.verify("op@example.com", "123456");

        assertEquals(AccountVerificationOutcome.SUCCESS, outcome);
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        verify(userAccountService).save(account);
    }

    @Test
    void verifyRejectsAnIncorrectCodeWithoutTouchingTheAccount() {
        when(otpService.verify("op@example.com", OtpPurpose.REGISTRATION_VERIFY, "000000"))
                .thenReturn(OtpVerificationResult.INCORRECT);

        AccountVerificationOutcome outcome = service.verify("op@example.com", "000000");

        assertEquals(AccountVerificationOutcome.INCORRECT_OR_EXPIRED, outcome);
        verify(userAccountService, never()).save(any());
    }

    @Test
    void verifyRejectsAnExpiredCode() {
        when(otpService.verify("op@example.com", OtpPurpose.REGISTRATION_VERIFY, "123456"))
                .thenReturn(OtpVerificationResult.EXPIRED);

        AccountVerificationOutcome outcome = service.verify("op@example.com", "123456");

        assertEquals(AccountVerificationOutcome.INCORRECT_OR_EXPIRED, outcome);
    }

    @Test
    void verifyRejectsOnceTooManyAttempts() {
        when(otpService.verify("op@example.com", OtpPurpose.REGISTRATION_VERIFY, "123456"))
                .thenReturn(OtpVerificationResult.TOO_MANY_ATTEMPTS);

        AccountVerificationOutcome outcome = service.verify("op@example.com", "123456");

        assertEquals(AccountVerificationOutcome.TOO_MANY_ATTEMPTS, outcome);
    }

    @Test
    void resendSendsANewCodeWhenNoCooldownIsActive() {
        UserAccount account = new UserAccount();
        account.setStatus(AccountStatus.PENDING_VERIFICATION);
        when(userAccountRepository.findByEmailIgnoreCase("op@example.com")).thenReturn(Optional.of(account));
        when(otpService.secondsUntilResendAllowed("op@example.com", OtpPurpose.REGISTRATION_VERIFY, 60))
                .thenReturn(0L);
        when(otpService.generate("op@example.com", OtpPurpose.REGISTRATION_VERIFY)).thenReturn("111111");

        long secondsRemaining = service.resend("op@example.com");

        assertEquals(0, secondsRemaining);
        verify(emailEventPublisher).publish(eq("op@example.com"), anyString(), anyString());
    }

    @Test
    void resendRefusesWhileOnCooldown() {
        UserAccount account = new UserAccount();
        account.setStatus(AccountStatus.PENDING_VERIFICATION);
        when(userAccountRepository.findByEmailIgnoreCase("op@example.com")).thenReturn(Optional.of(account));
        when(otpService.secondsUntilResendAllowed("op@example.com", OtpPurpose.REGISTRATION_VERIFY, 60))
                .thenReturn(42L);

        long secondsRemaining = service.resend("op@example.com");

        assertEquals(42, secondsRemaining);
        verify(emailEventPublisher, never()).publish(anyString(), anyString(), anyString());
    }

    @Test
    void resendIsASilentNoOpForAnUnknownEmail_noEnumeration() {
        when(userAccountRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        long secondsRemaining = service.resend("nobody@example.com");

        assertEquals(0, secondsRemaining);
        verify(emailEventPublisher, never()).publish(anyString(), anyString(), anyString());
    }

    @Test
    void resendIsASilentNoOpForAnAlreadyActiveAccount() {
        UserAccount account = new UserAccount();
        account.setStatus(AccountStatus.ACTIVE);
        when(userAccountRepository.findByEmailIgnoreCase("active@example.com")).thenReturn(Optional.of(account));

        long secondsRemaining = service.resend("active@example.com");

        assertEquals(0, secondsRemaining);
        verify(emailEventPublisher, never()).publish(anyString(), anyString(), anyString());
    }
}

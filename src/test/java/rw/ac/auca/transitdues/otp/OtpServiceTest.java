package rw.ac.auca.transitdues.otp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpVerificationRepository otpVerificationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private OtpService otpService;

    private OtpService newService() {
        OtpService service = new OtpService(otpVerificationRepository, passwordEncoder);
        ReflectionTestUtils.setField(service, "expiryMinutes", 10L);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        return service;
    }

    @Test
    void generateReturnsASixDigitCodeAndInvalidatesEarlierOnes() {
        otpService = newService();
        OtpVerification earlier = new OtpVerification();
        earlier.setConsumed(false);
        when(otpVerificationRepository.findByEmailIgnoreCaseAndPurposeAndConsumedFalse("op@example.com",
                OtpPurpose.PASSWORD_RESET)).thenReturn(List.of(earlier));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        String code = otpService.generate("OP@example.com ".trim(), OtpPurpose.PASSWORD_RESET);

        assertEquals(6, code.length());
        assertTrue(code.chars().allMatch(Character::isDigit));
        assertTrue(earlier.isConsumed());
        verify(otpVerificationRepository, times(2)).save(any(OtpVerification.class));

        ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpVerificationRepository, times(2)).save(captor.capture());
        OtpVerification newOtp = captor.getAllValues().get(1);
        assertEquals("op@example.com", newOtp.getEmail());
        assertEquals("hashed", newOtp.getCodeHash());
    }

    @Test
    void verifySucceedsWithTheCorrectCode() {
        otpService = newService();
        OtpVerification otp = new OtpVerification();
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setMaxAttempts(5);
        otp.setAttempts(0);
        otp.setCodeHash("hashed");
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                "op@example.com", OtpPurpose.PASSWORD_RESET)).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);

        OtpVerificationResult result = otpService.verify("op@example.com", OtpPurpose.PASSWORD_RESET, "123456");

        assertEquals(OtpVerificationResult.VERIFIED, result);
        assertTrue(otp.isConsumed());
    }

    @Test
    void verifyFailsWithAnIncorrectCodeAndCountsTheAttempt() {
        otpService = newService();
        OtpVerification otp = new OtpVerification();
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setMaxAttempts(5);
        otp.setAttempts(0);
        otp.setCodeHash("hashed");
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                "op@example.com", OtpPurpose.PASSWORD_RESET)).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("000000", "hashed")).thenReturn(false);

        OtpVerificationResult result = otpService.verify("op@example.com", OtpPurpose.PASSWORD_RESET, "000000");

        assertEquals(OtpVerificationResult.INCORRECT, result);
        assertEquals(1, otp.getAttempts());
        org.junit.jupiter.api.Assertions.assertFalse(otp.isConsumed());
    }

    @Test
    void verifyRejectsAnExpiredCodeWithoutCountingAnAttempt() {
        otpService = newService();
        OtpVerification otp = new OtpVerification();
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        otp.setMaxAttempts(5);
        otp.setAttempts(0);
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                "op@example.com", OtpPurpose.PASSWORD_RESET)).thenReturn(Optional.of(otp));

        OtpVerificationResult result = otpService.verify("op@example.com", OtpPurpose.PASSWORD_RESET, "123456");

        assertEquals(OtpVerificationResult.EXPIRED, result);
        verify(otpVerificationRepository, never()).save(any());
    }

    @Test
    void verifyRejectsOnceTheAttemptLimitIsReached() {
        otpService = newService();
        OtpVerification otp = new OtpVerification();
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setMaxAttempts(5);
        otp.setAttempts(5);
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                "op@example.com", OtpPurpose.PASSWORD_RESET)).thenReturn(Optional.of(otp));

        OtpVerificationResult result = otpService.verify("op@example.com", OtpPurpose.PASSWORD_RESET, "123456");

        assertEquals(OtpVerificationResult.TOO_MANY_ATTEMPTS, result);
    }

    @Test
    void secondsUntilResendAllowedIsZeroWhenNoCodeWasEverGenerated() {
        otpService = newService();
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc("op@example.com",
                OtpPurpose.REGISTRATION_VERIFY)).thenReturn(Optional.empty());

        long seconds = otpService.secondsUntilResendAllowed("op@example.com", OtpPurpose.REGISTRATION_VERIFY, 60);

        assertEquals(0, seconds);
    }

    @Test
    void secondsUntilResendAllowedIsPositiveRightAfterGenerating() {
        otpService = newService();
        OtpVerification latest = new OtpVerification();
        latest.setCreatedAt(LocalDateTime.now());
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc("op@example.com",
                OtpPurpose.REGISTRATION_VERIFY)).thenReturn(Optional.of(latest));

        long seconds = otpService.secondsUntilResendAllowed("op@example.com", OtpPurpose.REGISTRATION_VERIFY, 60);

        assertTrue(seconds > 0 && seconds <= 60);
    }

    @Test
    void secondsUntilResendAllowedIsZeroOnceTheCooldownHasPassed() {
        otpService = newService();
        OtpVerification latest = new OtpVerification();
        latest.setCreatedAt(LocalDateTime.now().minusSeconds(61));
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc("op@example.com",
                OtpPurpose.REGISTRATION_VERIFY)).thenReturn(Optional.of(latest));

        long seconds = otpService.secondsUntilResendAllowed("op@example.com", OtpPurpose.REGISTRATION_VERIFY, 60);

        assertEquals(0, seconds);
    }

    @Test
    void verifyReturnsNotFoundWhenThereIsNoUnconsumedCode() {
        otpService = newService();
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                "op@example.com", OtpPurpose.PASSWORD_RESET)).thenReturn(Optional.empty());

        OtpVerificationResult result = otpService.verify("op@example.com", OtpPurpose.PASSWORD_RESET, "123456");

        assertEquals(OtpVerificationResult.NOT_FOUND, result);
    }
}

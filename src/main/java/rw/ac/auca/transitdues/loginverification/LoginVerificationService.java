package rw.ac.auca.transitdues.loginverification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.email.EmailEventPublisher;
import rw.ac.auca.transitdues.otp.OtpPurpose;
import rw.ac.auca.transitdues.otp.OtpService;
import rw.ac.auca.transitdues.otp.OtpVerificationResult;
import rw.ac.auca.transitdues.user.domain.AccountStatus;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.util.Locale;
import java.util.Optional;

/**
 * The second factor of password login: once a password has already been
 * verified correct by the real {@code DaoAuthenticationProvider} (see
 * {@code OtpGatedAuthenticationProvider}), this is what sends and checks the
 * OTP that must follow it before a session is ever created. Reuses the exact
 * OTP/email machinery AccountVerificationService/PasswordResetService already
 * use, just with {@link OtpPurpose#LOGIN_VERIFY} - an OTP generated here can
 * never satisfy a registration-verify or password-reset check, and vice
 * versa, since OtpService's lookups are always scoped by (email, purpose).
 */
@Service
@RequiredArgsConstructor
public class LoginVerificationService {

    private final UserAccountRepository userAccountRepository;
    private final OtpService otpService;
    private final EmailEventPublisher emailEventPublisher;
    private final AuditLogService auditLogService;

    @Value("${app.otp.expiry-minutes:10}")
    private long otpExpiryMinutes;

    @Value("${app.otp.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    /** Called only after a password has already checked out correct. */
    @Transactional
    public void sendLoginCode(String email) {
        String normalized = normalize(email);
        String code = otpService.generate(normalized, OtpPurpose.LOGIN_VERIFY);
        String body = "Your TransitDues sign-in code is " + code + ". It expires in " + otpExpiryMinutes
                + " minutes. If this wasn't you, change your password immediately.";
        emailEventPublisher.publish(normalized, "Your TransitDues sign-in code", body);
    }

    @Transactional
    public LoginVerificationOutcome verify(String email, String otpCode) {
        String normalized = normalize(email);
        OtpVerificationResult otpResult = otpService.verify(normalized, OtpPurpose.LOGIN_VERIFY, otpCode);
        if (otpResult != OtpVerificationResult.VERIFIED) {
            return LoginVerificationOutcome.fromOtpResult(otpResult);
        }

        Optional<UserAccount> account = userAccountRepository.findByEmailIgnoreCase(normalized);
        if (account.isEmpty() || account.get().getStatus() != AccountStatus.ACTIVE || !account.get().isEnabled()) {
            // The OTP verified but the account vanished or was disabled in the
            // meantime - vanishingly unlikely, but still the same generic message.
            return LoginVerificationOutcome.INCORRECT_OR_EXPIRED;
        }

        auditLogService.record("UserAccount", account.get().getId().toString(), "LOGIN_VERIFY",
                "Signed in after login OTP verification", normalized);
        return LoginVerificationOutcome.SUCCESS;
    }

    /**
     * Resends a login code. Returns 0 if a code was (re)sent, or the number of
     * seconds still remaining on the cooldown otherwise. Silently does
     * nothing (but still returns 0, i.e. "looks sent") for an email with no
     * active account - same no-enumeration reasoning as everywhere else OTP
     * is used.
     */
    @Transactional
    public long resend(String email) {
        String normalized = normalize(email);
        Optional<UserAccount> account = userAccountRepository.findByEmailIgnoreCase(normalized);
        if (account.isEmpty() || account.get().getStatus() != AccountStatus.ACTIVE || !account.get().isEnabled()) {
            return 0;
        }

        long secondsRemaining = otpService.secondsUntilResendAllowed(normalized, OtpPurpose.LOGIN_VERIFY,
                resendCooldownSeconds);
        if (secondsRemaining > 0) {
            return secondsRemaining;
        }

        sendLoginCode(normalized);
        return 0;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

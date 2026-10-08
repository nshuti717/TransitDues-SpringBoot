package rw.ac.auca.transitdues.verification;

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
import rw.ac.auca.transitdues.user.service.UserAccountService;

import java.util.Locale;
import java.util.Optional;

/**
 * Owns the email-verification step a self-registered operator must complete
 * before their account becomes usable. Reuses the same OTP/email/no-
 * enumeration patterns PasswordResetService already established.
 */
@Service
@RequiredArgsConstructor
public class AccountVerificationService {

    private final UserAccountRepository userAccountRepository;
    private final UserAccountService userAccountService;
    private final OtpService otpService;
    private final EmailEventPublisher emailEventPublisher;
    private final AuditLogService auditLogService;

    @Value("${app.otp.expiry-minutes:10}")
    private long otpExpiryMinutes;

    @Value("${app.otp.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    /** Generates a fresh code and emails it. Used by registration and by resend. */
    @Transactional
    public void sendVerificationCode(String email) {
        String normalized = normalize(email);
        String code = otpService.generate(normalized, OtpPurpose.REGISTRATION_VERIFY);
        String body = "Your TransitDues verification code is " + code + ". It expires in " + otpExpiryMinutes
                + " minutes. If you did not create this account, you can safely ignore this email.";
        emailEventPublisher.publish(normalized, "Verify your TransitDues account", body);
    }

    @Transactional
    public AccountVerificationOutcome verify(String email, String otpCode) {
        String normalized = normalize(email);
        OtpVerificationResult otpResult = otpService.verify(normalized, OtpPurpose.REGISTRATION_VERIFY, otpCode);
        if (otpResult != OtpVerificationResult.VERIFIED) {
            return AccountVerificationOutcome.fromOtpResult(otpResult);
        }

        Optional<UserAccount> account = userAccountRepository.findByEmailIgnoreCase(normalized);
        if (account.isEmpty()) {
            // The OTP verified but the account is gone - vanishingly unlikely, but
            // still answered with the same generic message, not a distinct one.
            return AccountVerificationOutcome.INCORRECT_OR_EXPIRED;
        }

        UserAccount userAccount = account.get();
        userAccount.setStatus(AccountStatus.ACTIVE);
        userAccountService.save(userAccount);
        auditLogService.record("UserAccount", userAccount.getId().toString(), "ACTIVATE",
                "Operator email verified", normalized);
        return AccountVerificationOutcome.SUCCESS;
    }

    /**
     * Resends a verification code. Returns 0 if a code was (re)sent, or the
     * number of seconds still remaining on the cooldown otherwise. Silently
     * does nothing (but still returns 0, i.e. "looks sent") for an email with
     * no pending registration - same no-enumeration reasoning as everywhere
     * else OTP is used.
     */
    @Transactional
    public long resend(String email) {
        String normalized = normalize(email);
        Optional<UserAccount> account = userAccountRepository.findByEmailIgnoreCase(normalized);
        if (account.isEmpty() || account.get().getStatus() != AccountStatus.PENDING_VERIFICATION) {
            return 0;
        }

        long secondsRemaining = otpService.secondsUntilResendAllowed(normalized, OtpPurpose.REGISTRATION_VERIFY,
                resendCooldownSeconds);
        if (secondsRemaining > 0) {
            return secondsRemaining;
        }

        sendVerificationCode(normalized);
        return 0;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

package rw.ac.auca.transitdues.passwordreset;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.email.EmailEventPublisher;
import rw.ac.auca.transitdues.otp.OtpPurpose;
import rw.ac.auca.transitdues.otp.OtpService;
import rw.ac.auca.transitdues.otp.OtpVerificationResult;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;
import rw.ac.auca.transitdues.user.service.UserAccountService;

import java.util.Locale;
import java.util.Optional;

/**
 * Backs the real /forgot-password -&gt; /reset-password flow (replacing the
 * fake "Forgot password?" link removed in an earlier UI-honesty pass - see
 * spec.md Session 1).
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserAccountRepository userAccountRepository;
    private final UserAccountService userAccountService;
    private final OtpService otpService;
    private final EmailEventPublisher emailEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Value("${app.otp.expiry-minutes:10}")
    private long otpExpiryMinutes;

    /**
     * Always succeeds from the caller's point of view, whether or not the
     * email belongs to an account - revealing that distinction would let an
     * attacker enumerate registered emails.
     */
    @Transactional
    public void requestReset(String email) {
        String normalized = normalize(email);
        if (userAccountRepository.findByEmailIgnoreCase(normalized).isEmpty()) {
            return;
        }

        String code = otpService.generate(normalized, OtpPurpose.PASSWORD_RESET);
        String body = "Your TransitDues password reset code is " + code + ". It expires in " + otpExpiryMinutes
                + " minutes. If you did not request this, you can safely ignore this email.";
        emailEventPublisher.publish(normalized, "Your TransitDues password reset code", body);
    }

    @Transactional
    public PasswordResetOutcome resetPassword(String email, String otpCode, String newPassword) {
        String normalized = normalize(email);
        OtpVerificationResult otpResult = otpService.verify(normalized, OtpPurpose.PASSWORD_RESET, otpCode);
        if (otpResult != OtpVerificationResult.VERIFIED) {
            return PasswordResetOutcome.fromOtpResult(otpResult);
        }

        Optional<UserAccount> account = userAccountRepository.findByEmailIgnoreCase(normalized);
        if (account.isEmpty()) {
            // The OTP verified but the account is gone - vanishingly unlikely, but
            // still answered with the same generic message, not a distinct one.
            return PasswordResetOutcome.INCORRECT_OR_EXPIRED;
        }

        UserAccount userAccount = account.get();
        userAccount.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountService.save(userAccount);
        auditLogService.record("UserAccount", userAccount.getId().toString(), "UPDATE",
                "Password reset via OTP");
        return PasswordResetOutcome.SUCCESS;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

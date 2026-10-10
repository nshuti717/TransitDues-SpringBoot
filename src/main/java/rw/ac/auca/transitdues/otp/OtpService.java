package rw.ac.auca.transitdues.otp;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Generates and verifies one-time codes. A code is a random 6 digits, stored
 * only as a {@link PasswordEncoder} hash with an expiry and a capped number
 * of guesses; generating a new one invalidates any earlier unconsumed code
 * for the same email+purpose so only the latest code is ever valid.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpVerificationRepository otpVerificationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.otp.expiry-minutes:10}")
    private long expiryMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    /**
     * Generates a new code, invalidating any earlier unconsumed one for this
     * email+purpose. Returns the plain 6-digit code so the caller can send
     * it (e.g. by email) - it is never itself persisted or logged.
     */
    @Transactional
    public String generate(String email, OtpPurpose purpose) {
        String normalizedEmail = normalize(email);
        otpVerificationRepository.findByEmailIgnoreCaseAndPurposeAndConsumedFalse(normalizedEmail, purpose)
                .forEach(existing -> {
                    existing.setConsumed(true);
                    otpVerificationRepository.save(existing);
                });

        String code = generateSixDigitCode();

        OtpVerification otp = new OtpVerification();
        otp.setEmail(normalizedEmail);
        otp.setPurpose(purpose);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        otp.setMaxAttempts(maxAttempts);
        otpVerificationRepository.save(otp);

        return code;
    }

    /**
     * Checks a submitted code against the latest unconsumed one for this
     * email+purpose. An incorrect guess still counts against the attempt
     * limit; once verified (or once the limit is hit), the code can never be
     * used again.
     */
    @Transactional
    public OtpVerificationResult verify(String email, OtpPurpose purpose, String submittedCode) {
        Optional<OtpVerification> maybeOtp = otpVerificationRepository
                .findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(normalize(email), purpose);
        if (maybeOtp.isEmpty()) {
            return OtpVerificationResult.NOT_FOUND;
        }

        OtpVerification otp = maybeOtp.get();
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            return OtpVerificationResult.EXPIRED;
        }
        if (otp.getAttempts() >= otp.getMaxAttempts()) {
            return OtpVerificationResult.TOO_MANY_ATTEMPTS;
        }

        otp.setAttempts(otp.getAttempts() + 1);
        if (submittedCode == null || !passwordEncoder.matches(submittedCode, otp.getCodeHash())) {
            otpVerificationRepository.save(otp);
            return OtpVerificationResult.INCORRECT;
        }

        otp.setConsumed(true);
        otpVerificationRepository.save(otp);
        return OtpVerificationResult.VERIFIED;
    }

    /**
     * How many seconds must still pass before a new code may be requested for
     * this email+purpose, based on when the most recent one (consumed or not)
     * was generated. Zero means a resend is allowed right now. Enforced
     * server-side so a "resend" button's cooldown can't be bypassed by
     * replaying the request.
     */
    public long secondsUntilResendAllowed(String email, OtpPurpose purpose, long cooldownSeconds) {
        Optional<OtpVerification> latest = otpVerificationRepository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(normalize(email), purpose);
        if (latest.isEmpty()) {
            return 0;
        }

        LocalDateTime nextAllowedAt = latest.get().getCreatedAt().plusSeconds(cooldownSeconds);
        long remaining = Duration.between(LocalDateTime.now(), nextAllowedAt).getSeconds();
        return Math.max(0, remaining);
    }

    private String generateSixDigitCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

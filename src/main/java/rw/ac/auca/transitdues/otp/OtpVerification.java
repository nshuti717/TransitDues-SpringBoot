package rw.ac.auca.transitdues.otp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import rw.ac.auca.transitdues.base.BaseEntity;

import java.time.LocalDateTime;

/**
 * A one-time code, stored only as a hash - never in plain text - with an
 * expiry, a limited number of guess attempts, and a consumed flag so it can
 * never be reused. See {@link OtpService} for the generate/verify logic.
 */
@Entity
@Table(name = "otp_verification", indexes = {
        @Index(name = "idx_otp_email_purpose_consumed", columnList = "email, purpose, consumed")
})
@Getter
@Setter
@NoArgsConstructor
public class OtpVerification extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpPurpose purpose;

    /** BCrypt hash of the 6-digit code. The plain code is never persisted or logged. */
    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(nullable = false)
    private boolean consumed = false;
}

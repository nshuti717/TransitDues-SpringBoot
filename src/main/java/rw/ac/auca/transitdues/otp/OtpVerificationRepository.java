package rw.ac.auca.transitdues.otp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, java.util.UUID> {

    List<OtpVerification> findByEmailIgnoreCaseAndPurposeAndConsumedFalse(String email, OtpPurpose purpose);

    Optional<OtpVerification> findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
            String email, OtpPurpose purpose);

    /** Newest row regardless of consumed status - used for resend cooldown checks. */
    Optional<OtpVerification> findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);
}

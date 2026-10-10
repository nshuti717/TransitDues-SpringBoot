package rw.ac.auca.transitdues.otp;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate's ddl-auto=update generates a CHECK constraint on an
 * {@code @Enumerated(STRING)} column only once, when the table is first
 * created - it never widens that constraint when a new enum constant is
 * added later. otp_verification.purpose was created back when OtpPurpose had
 * only PASSWORD_RESET, so its check constraint rejects the newer
 * REGISTRATION_VERIFY value outright on any database that already had this
 * table before that constant existed. A brand-new database never hits this
 * (Hibernate generates the constraint from the full, current enum the first
 * time it creates the table) - this only matters for upgrading an existing
 * one, same situation LegacyDuePaymentMigrationRunner handles for
 * due_payment. Dropping the constraint is safe: the enum is still fully
 * enforced at the application layer by @Enumerated(STRING) itself.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class OtpPurposeConstraintMigrationRunner implements ApplicationRunner {

    private static final String DROP_STALE_CHECK_SQL =
            "ALTER TABLE otp_verification DROP CONSTRAINT IF EXISTS otp_verification_purpose_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(DROP_STALE_CHECK_SQL);
    }
}

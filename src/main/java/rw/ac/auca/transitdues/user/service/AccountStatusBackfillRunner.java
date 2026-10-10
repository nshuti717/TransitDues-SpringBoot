package rw.ac.auca.transitdues.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Backfills user_account rows written before the status column existed (every
 * account that pre-dates email-verified self-registration) to ACTIVE, the same
 * way LegacyDuePaymentMigrationRunner backfills due_payment rows that pre-date
 * its own newer columns. Idempotent and safe to run on every startup - once
 * every row has a status, this is a no-op.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AccountStatusBackfillRunner implements ApplicationRunner {

    private static final String BACKFILL_SQL = "UPDATE user_account SET status = 'ACTIVE' WHERE status IS NULL";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int migratedCount = jdbcTemplate.update(BACKFILL_SQL);
        if (migratedCount > 0) {
            log.warn("Backfilled {} user_account row(s) with no status to ACTIVE "
                    + "(pre-dates email-verified self-registration)", migratedCount);
        }
    }
}

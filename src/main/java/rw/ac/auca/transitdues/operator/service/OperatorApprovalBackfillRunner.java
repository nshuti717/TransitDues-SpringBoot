package rw.ac.auca.transitdues.operator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Backfills operator rows written before the approval_status column existed
 * (every operator created prior to this feature) to ACTIVE, the same way
 * AccountStatusBackfillRunner backfills user_account rows that pre-date its
 * own status column. Idempotent and safe to run on every startup - once every
 * row has a status, this is a no-op. This is what keeps existing, already-
 * operating operators from being accidentally treated as pending approval.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class OperatorApprovalBackfillRunner implements ApplicationRunner {

    private static final String BACKFILL_SQL =
            "UPDATE operator SET approval_status = 'ACTIVE' WHERE approval_status IS NULL";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int migratedCount = jdbcTemplate.update(BACKFILL_SQL);
        if (migratedCount > 0) {
            log.warn("Backfilled {} operator row(s) with no approval_status to ACTIVE "
                    + "(pre-dates the operator approval workflow)", migratedCount);
        }
    }
}

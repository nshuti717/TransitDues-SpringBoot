package rw.ac.auca.transitdues.duepayment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Backfills rows written before dueDate/paidAt/status (as an enum) existed.
 * This runs as plain JDBC, not through the DuePayment JPA entity: the old
 * status column holds values like "Paid"/"Pending" that do not match the
 * DuePaymentStatus enum's constant names, so loading an un-migrated row
 * through Hibernate (which happens as soon as anything calls
 * DuePaymentRepository.findAll() or similar) would throw. Going straight to
 * SQL for this one-time fix avoids that entirely.
 *
 * The due_date column is only ever null for a row that pre-dates this
 * migration (every row created through the application sets it), so the
 * backfill statement is naturally idempotent and safe to run on every
 * startup. Relaxing date_paid's NOT NULL is idempotent too (Postgres does
 * not error on dropping a constraint that is already gone).
 *
 * date_paid itself is no longer written by the application (dueDate/paidAt
 * replace it), but its original NOT NULL constraint stays in the database
 * until this runs - every new row insert would otherwise fail that
 * constraint, since new code never sets this column at all.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LegacyDuePaymentMigrationRunner implements ApplicationRunner {

    private static final String DROP_DATE_PAID_NOT_NULL_SQL =
            "ALTER TABLE due_payment ALTER COLUMN date_paid DROP NOT NULL";

    private static final String BACKFILL_SQL =
            "UPDATE due_payment SET status = 'PAID', due_date = date_paid, paid_at = date_paid "
                    + "WHERE due_date IS NULL";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(DROP_DATE_PAID_NOT_NULL_SQL);

        int migratedCount = jdbcTemplate.update(BACKFILL_SQL);
        if (migratedCount > 0) {
            log.warn("Migrated {} legacy due_payment row(s): status set to PAID, due_date and paid_at "
                    + "backfilled from date_paid", migratedCount);
        }
    }
}

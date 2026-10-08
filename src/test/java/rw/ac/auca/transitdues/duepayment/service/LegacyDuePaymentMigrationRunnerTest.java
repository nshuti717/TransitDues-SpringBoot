package rw.ac.auca.transitdues.duepayment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyDuePaymentMigrationRunnerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LegacyDuePaymentMigrationRunner legacyDuePaymentMigrationRunner;

    @Test
    void backfillsStatusDueDateAndPaidAtForRowsThatPreDateTheseColumns() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.update(sqlCaptor.capture())).thenReturn(3);

        legacyDuePaymentMigrationRunner.run(null);

        verify(jdbcTemplate).execute("ALTER TABLE due_payment ALTER COLUMN date_paid DROP NOT NULL");

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("due_payment"), "should target the due_payment table");
        assertTrue(sql.contains("status = 'PAID'"), "should set status to the PAID enum constant");
        assertTrue(sql.contains("due_date = date_paid"), "should backfill dueDate from the old date_paid column");
        assertTrue(sql.contains("paid_at = date_paid"), "should backfill paidAt from the old date_paid column");
        assertTrue(sql.contains("due_date IS NULL"), "should only touch rows that have never been migrated");
    }
}

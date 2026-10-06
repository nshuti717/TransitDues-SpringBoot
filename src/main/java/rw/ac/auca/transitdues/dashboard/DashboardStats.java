package rw.ac.auca.transitdues.dashboard;

import java.math.BigDecimal;
import java.util.SortedMap;

/**
 * Plain immutable snapshot of the dashboard numbers. This is what gets cached in
 * Redis, never a JPA entity.
 */
public record DashboardStats(
        int totalStages,
        int totalOperators,
        int totalDuePayments,
        BigDecimal totalAmountCollected,
        SortedMap<String, Long> paymentCountsByStatus) {
}

package rw.ac.auca.transitdues.finance;

import java.math.BigDecimal;
import java.util.SortedMap;

/**
 * Finance collections dashboard numbers, computed fresh on every view (not
 * cached, unlike DashboardStats - this page is used for day-to-day collection
 * work where finance expects up-to-the-second figures).
 */
public record CollectionsSummary(
        BigDecimal totalExpected,
        BigDecimal totalCollected,
        BigDecimal totalOutstanding,
        SortedMap<String, Long> countsByStatus) {
}

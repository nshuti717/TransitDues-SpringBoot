package rw.ac.auca.transitdues.finance;

import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.DuePaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes the finance collections summary and applies the collections
 * page's filters. Kept separate from DashboardStatsService: that one is
 * cached (60s TTL, used for the at-a-glance main dashboard), this one is
 * always fresh (used for day-to-day collection work) and supports filtering,
 * which the cached dashboard does not need.
 */
@Service
public class CollectionsService {

    public CollectionsSummary summarize(List<DuePayment> duePayments) {
        BigDecimal totalExpected = duePayments.stream()
                .map(DuePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollected = duePayments.stream()
                .filter(duePayment -> duePayment.getStatus() == DuePaymentStatus.PAID)
                .map(DuePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SortedMap<String, Long> countsByStatus = duePayments.stream()
                .collect(Collectors.groupingBy(duePayment -> duePayment.getEffectiveStatus().name(),
                        TreeMap::new, Collectors.counting()));

        return new CollectionsSummary(totalExpected, totalCollected,
                totalExpected.subtract(totalCollected), countsByStatus);
    }

    public List<DuePayment> filter(List<DuePayment> duePayments, String status, UUID stageId, String operatorQuery,
                                    LocalDate fromDate, LocalDate toDate) {
        return duePayments.stream()
                .filter(due -> status == null || status.isBlank()
                        || due.getEffectiveStatus().name().equalsIgnoreCase(status))
                .filter(due -> stageId == null || stageId.equals(due.getOperator().getStage().getId()))
                .filter(due -> operatorQuery == null || operatorQuery.isBlank() || matchesOperator(due, operatorQuery))
                .filter(due -> fromDate == null || !due.getDueDate().isBefore(fromDate))
                .filter(due -> toDate == null || !due.getDueDate().isAfter(toDate))
                .toList();
    }

    private boolean matchesOperator(DuePayment due, String query) {
        String needle = query.trim().toLowerCase();
        return due.getOperator().getFullName().toLowerCase().contains(needle)
                || due.getOperator().getPlateNumber().toLowerCase().contains(needle);
    }
}

package rw.ac.auca.transitdues.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.DuePaymentStatus;
import rw.ac.auca.transitdues.duepayment.service.DuePaymentService;
import rw.ac.auca.transitdues.operator.service.OperatorService;
import rw.ac.auca.transitdues.stage.service.StageService;

import java.math.BigDecimal;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardStatsService {

    private final StageService stageService;
    private final OperatorService operatorService;
    private final DuePaymentService duePaymentService;

    @Cacheable("dashboardStats")
    public DashboardStats getStats() {
        long startedAtNanos = System.nanoTime();

        int totalStages = stageService.findAllStages().size();
        int totalOperators = operatorService.findAllOperators().size();
        List<DuePayment> duePayments = duePaymentService.findAllDuePayments();

        BigDecimal totalAmountCollected = duePayments.stream()
                .filter(duePayment -> duePayment.getStatus() == DuePaymentStatus.PAID)
                .map(DuePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SortedMap<String, Long> paymentCountsByStatus = duePayments.stream()
                .collect(Collectors.groupingBy(duePayment -> duePayment.getEffectiveStatus().name(),
                        TreeMap::new, Collectors.counting()));

        DashboardStats stats = new DashboardStats(totalStages, totalOperators, duePayments.size(),
                totalAmountCollected, paymentCountsByStatus);

        long elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        log.info("DASHBOARD CACHE MISS: computed in {} ms", elapsedMillis);

        return stats;
    }
}

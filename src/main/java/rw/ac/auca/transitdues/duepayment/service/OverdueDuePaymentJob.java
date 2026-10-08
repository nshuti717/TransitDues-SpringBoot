package rw.ac.auca.transitdues.duepayment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.DuePaymentStatus;
import rw.ac.auca.transitdues.duepayment.repository.DuePaymentRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Flips any PENDING due whose dueDate has passed to OVERDUE: once daily, just
 * after midnight Africa/Kigali time, and once at startup (so a due that went
 * overdue while the app was down is not left stale until the next midnight).
 *
 * This is run through ApplicationRunner.run() and @Scheduled on the SAME
 * public method deliberately, not via a self-invoked private helper: cache
 * eviction below is done by hand (CacheManager.getCache(...).clear()) rather
 * than @CacheEvict, specifically because @CacheEvict only works through
 * Spring's proxy and run() calling this@Scheduled method directly, in-class,
 * would bypass that proxy and silently skip the eviction.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class OverdueDuePaymentJob implements ApplicationRunner {

    private static final ZoneId KIGALI_ZONE = ZoneId.of("Africa/Kigali");

    private final DuePaymentRepository duePaymentRepository;
    private final AuditLogService auditLogService;
    private final CacheManager cacheManager;

    @Override
    public void run(ApplicationArguments args) {
        markOverdueDuePayments();
    }

    @Scheduled(cron = "0 1 0 * * *", zone = "Africa/Kigali")
    public void markOverdueDuePayments() {
        List<DuePayment> overdueDuePayments = duePaymentRepository.findByStatusAndDueDateBefore(
                DuePaymentStatus.PENDING, LocalDate.now(KIGALI_ZONE));

        for (DuePayment duePayment : overdueDuePayments) {
            duePayment.setStatus(DuePaymentStatus.OVERDUE);
            duePaymentRepository.save(duePayment);
            auditLogService.record("DuePayment", duePayment.getId().toString(), "UPDATE",
                    "Marked OVERDUE (due date " + duePayment.getDueDate() + ")");
        }

        if (!overdueDuePayments.isEmpty()) {
            Cache dashboardStatsCache = cacheManager.getCache("dashboardStats");
            if (dashboardStatsCache != null) {
                dashboardStatsCache.clear();
            }
            log.warn("Marked {} due payment(s) as OVERDUE", overdueDuePayments.size());
        }
    }
}

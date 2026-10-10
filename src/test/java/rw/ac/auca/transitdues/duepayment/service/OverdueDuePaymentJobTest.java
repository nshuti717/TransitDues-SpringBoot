package rw.ac.auca.transitdues.duepayment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.DuePaymentStatus;
import rw.ac.auca.transitdues.duepayment.repository.DuePaymentRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverdueDuePaymentJobTest {

    @Mock
    private DuePaymentRepository duePaymentRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache dashboardStatsCache;

    @InjectMocks
    private OverdueDuePaymentJob overdueDuePaymentJob;

    @Test
    void flipsOnlyThePastDuePendingRowsTheRepositoryReturns() {
        DuePayment pastDueOne = pendingDueDate(LocalDate.now().minusDays(5));
        DuePayment pastDueTwo = pendingDueDate(LocalDate.now().minusDays(1));

        // The repository query is what scopes this to PENDING + before today;
        // the job just has to correctly process whatever it is handed back.
        when(duePaymentRepository.findByStatusAndDueDateBefore(eq(DuePaymentStatus.PENDING), any(LocalDate.class)))
                .thenReturn(List.of(pastDueOne, pastDueTwo));
        when(cacheManager.getCache("dashboardStats")).thenReturn(dashboardStatsCache);

        overdueDuePaymentJob.markOverdueDuePayments();

        assertEquals(DuePaymentStatus.OVERDUE, pastDueOne.getStatus());
        assertEquals(DuePaymentStatus.OVERDUE, pastDueTwo.getStatus());
        verify(duePaymentRepository, times(2)).save(any(DuePayment.class));
        verify(auditLogService, times(2)).record(eq("DuePayment"), any(), eq("UPDATE"), any());
        verify(dashboardStatsCache).clear();
    }

    @Test
    void doesNothingAndSkipsCacheEvictionWhenNothingIsOverdue() {
        when(duePaymentRepository.findByStatusAndDueDateBefore(eq(DuePaymentStatus.PENDING), any(LocalDate.class)))
                .thenReturn(List.of());

        overdueDuePaymentJob.markOverdueDuePayments();

        verify(duePaymentRepository, never()).save(any(DuePayment.class));
        verify(cacheManager, never()).getCache(any());
    }

    private DuePayment pendingDueDate(LocalDate dueDate) {
        DuePayment duePayment = new DuePayment();
        duePayment.setId(UUID.randomUUID());
        duePayment.setStatus(DuePaymentStatus.PENDING);
        duePayment.setDueDate(dueDate);
        return duePayment;
    }
}

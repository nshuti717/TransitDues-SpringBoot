package rw.ac.auca.transitdues.duepayment.service;

import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DuePaymentService {

    DuePayment createDuePayment(DuePayment duePayment);

    DuePayment updateDuePayment(UUID id, DuePayment duePayment);

    void deleteDuePayment(UUID id);

    DuePayment findDuePaymentById(UUID id);

    List<DuePayment> findAllDuePayments();

    /**
     * Issues one PENDING due per operator in scope (all operators, or every
     * operator at one stage), skipping any operator who already has a due of
     * this type and dueDate. Runs as a single transaction.
     */
    BulkIssueResult bulkIssueDuePayments(PaymentType type, BigDecimal amount, LocalDate dueDate, UUID stageId);
}

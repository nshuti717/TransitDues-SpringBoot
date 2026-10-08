package rw.ac.auca.transitdues.duepayment.service;

import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.PaymentType;
import rw.ac.auca.transitdues.operator.domain.Operator;

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

    List<DuePayment> findDuePaymentsByOperator(UUID operatorId);

    /**
     * Issues one PENDING due per operator in scope (all operators, or every
     * operator at one stage), skipping any operator who already has a due of
     * this type and dueDate. Runs as a single transaction.
     */
    BulkIssueResult bulkIssueDuePayments(PaymentType type, BigDecimal amount, LocalDate dueDate, UUID stageId);

    /**
     * Starts an online payment attempt: moves a PENDING/FAILED due to
     * SUBMITTED. Rejects anyone but the due's own operator, and any due not
     * currently payable.
     */
    DuePayment initiateOnlinePayment(UUID duePaymentId, Operator payingOperator);

    /**
     * Resolves a SUBMITTED online payment to PAID or FAILED. Simulates a
     * payment gateway outcome since no real provider is integrated.
     */
    DuePayment confirmOnlinePayment(UUID duePaymentId, Operator payingOperator);

    /** Abandons a SUBMITTED online payment, returning the due to PENDING. */
    DuePayment cancelOnlinePayment(UUID duePaymentId, Operator payingOperator);

    /**
     * Operator asks to pay cash in person: moves a PENDING/FAILED due to
     * CASH_PENDING, awaiting finance confirmation.
     */
    DuePayment requestCashPayment(UUID duePaymentId, Operator payingOperator);

    /**
     * Finance confirms a cash payment was received: moves a CASH_PENDING due
     * to PAID. Finance-only by route, not by an ownership check - any
     * finance officer may confirm any operator's cash request.
     */
    DuePayment confirmCashPayment(UUID duePaymentId);

    /** Finance rejects a cash request: returns a CASH_PENDING due to PENDING. */
    DuePayment rejectCashPayment(UUID duePaymentId);
}

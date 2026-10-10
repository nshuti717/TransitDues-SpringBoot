package rw.ac.auca.transitdues.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DuePaymentEvent(
        String eventType,
        String duePaymentId,
        String operatorId,
        String operatorName,
        BigDecimal amount,
        String status,
        String performedBy,
        LocalDateTime occurredAt,
        String reference,
        String paymentMethod
) {

    public static final String CREATED = "duepayment.created";
    public static final String UPDATED = "duepayment.updated";
    public static final String PAYMENT_SUBMITTED = "duepayment.submitted";
    public static final String PAYMENT_CONFIRMED = "duepayment.paid";
    public static final String PAYMENT_FAILED = "duepayment.failed";
    public static final String PAYMENT_CASH_REQUESTED = "duepayment.cashrequested";
}

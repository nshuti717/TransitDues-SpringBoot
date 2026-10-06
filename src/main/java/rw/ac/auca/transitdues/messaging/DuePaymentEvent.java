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
        LocalDateTime occurredAt
) {

    public static final String CREATED = "duepayment.created";
    public static final String UPDATED = "duepayment.updated";
}

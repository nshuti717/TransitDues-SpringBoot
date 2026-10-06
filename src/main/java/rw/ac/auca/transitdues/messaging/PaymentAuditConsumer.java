package rw.ac.auca.transitdues.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import rw.ac.auca.transitdues.audit.PaymentEventLog;
import rw.ac.auca.transitdues.audit.PaymentEventLogRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAuditConsumer {

    private final PaymentEventLogRepository paymentEventLogRepository;

    /**
     * Runs on a RabbitMQ listener thread, which has no SecurityContext, so
     * {@code performedBy} is read from the event payload rather than
     * SecurityContextHolder.
     */
    @RabbitListener(queues = RabbitConfig.PAYMENT_AUDIT_QUEUE)
    public void handle(DuePaymentEvent event) {
        PaymentEventLog eventLog = new PaymentEventLog(null, event.eventType(), event.duePaymentId(),
                event.operatorId(), event.operatorName(), event.amount(), event.status(), event.performedBy(),
                event.occurredAt());
        paymentEventLogRepository.save(eventLog);
        log.info("Recorded payment event {} for DuePayment {}", event.eventType(), event.duePaymentId());
    }
}

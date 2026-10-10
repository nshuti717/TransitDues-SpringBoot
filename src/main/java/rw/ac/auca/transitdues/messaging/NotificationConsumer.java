package rw.ac.auca.transitdues.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Email and SMS delivery are simulated by logging, since no paid or external
 * provider is wired up for this deadline — but the RabbitMQ publish/consume path
 * that would trigger a real notification is real.
 */
@Slf4j
@Component
public class NotificationConsumer {

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    public void handle(DuePaymentEvent event) {
        log.info("[SIMULATED EMAIL] Payment of {} RWF recorded for operator {}", event.amount(),
                event.operatorName());
        log.info("[SIMULATED SMS] Payment of {} RWF recorded for operator {}", event.amount(), event.operatorName());
    }
}

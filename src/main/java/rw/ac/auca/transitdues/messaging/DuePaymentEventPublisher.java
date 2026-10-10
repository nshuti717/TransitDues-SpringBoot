package rw.ac.auca.transitdues.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DuePaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(DuePaymentEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, event.eventType(), event);
        } catch (Exception ex) {
            log.warn("Failed to publish {} event for DuePayment {}: {}", event.eventType(), event.duePaymentId(),
                    ex.getMessage(), ex);
        }
    }
}

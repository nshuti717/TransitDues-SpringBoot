package rw.ac.auca.transitdues.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import rw.ac.auca.transitdues.messaging.RabbitConfig;

import java.time.LocalDateTime;

/**
 * Publishes an email-to-send event to RabbitMQ; {@link EmailSendConsumer} is
 * the other side that actually sends it. Decoupled the same way
 * DuePaymentEventPublisher decouples due-payment audit/notification from the
 * request that triggered it - a publish failure here must not fail whatever
 * action (e.g. a password reset request) triggered it, so it is only logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String to, String subject, String body) {
        EmailEvent event = new EmailEvent(to, subject, body, LocalDateTime.now());
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, EmailEvent.SEND, event);
        } catch (Exception ex) {
            log.warn("Failed to publish email event to {}: {}", to, ex.getMessage(), ex);
        }
    }
}

package rw.ac.auca.transitdues.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import rw.ac.auca.transitdues.messaging.RabbitConfig;

/**
 * Consumes email-to-send events and actually sends them via {@link JavaMailSender}
 * (Mailpit in local dev, see docker-compose.yml). Unlike NotificationConsumer's
 * simulated email/SMS, this one is a real send - visible at http://localhost:8025
 * in dev. A send failure is logged, not retried or surfaced back to whoever
 * triggered the original request (that request already completed by the time this
 * listener runs), matching the fire-and-forget nature of the rest of this
 * messaging layer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSendConsumer {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @RabbitListener(queues = RabbitConfig.EMAIL_QUEUE)
    public void handle(EmailEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(event.to());
        message.setSubject(event.subject());
        message.setText(event.body());
        try {
            mailSender.send(message);
            log.info("Sent email to {}: {}", event.to(), event.subject());
        } catch (MailException ex) {
            log.error("Failed to send email to {}: {}", event.to(), ex.getMessage(), ex);
        }
    }
}

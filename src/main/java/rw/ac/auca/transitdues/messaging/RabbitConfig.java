package rw.ac.auca.transitdues.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "transitdues.events";
    public static final String PAYMENT_AUDIT_QUEUE = "transitdues.payment-audit.queue";
    public static final String NOTIFICATION_QUEUE = "transitdues.notification.queue";
    public static final String ROUTING_KEY_PATTERN = "duepayment.*";

    @Bean
    public TopicExchange transitDuesEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentAuditQueue() {
        return new Queue(PAYMENT_AUDIT_QUEUE, true);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(NOTIFICATION_QUEUE, true);
    }

    @Bean
    public Binding paymentAuditBinding(Queue paymentAuditQueue, TopicExchange transitDuesEventsExchange) {
        return BindingBuilder.bind(paymentAuditQueue).to(transitDuesEventsExchange).with(ROUTING_KEY_PATTERN);
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange transitDuesEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(transitDuesEventsExchange).with(ROUTING_KEY_PATTERN);
    }

    /**
     * Spring AMQP 4.1.1 ships {@code JacksonJsonMessageConverter}, built on this
     * project's Jackson 3 ({@code tools.jackson}) dependency, as the replacement for
     * the legacy Jackson 2 {@code Jackson2JsonMessageConverter}. Declaring it as the
     * single MessageConverter bean makes Boot's auto-configured RabbitTemplate and
     * @RabbitListener container factory pick it up automatically.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}

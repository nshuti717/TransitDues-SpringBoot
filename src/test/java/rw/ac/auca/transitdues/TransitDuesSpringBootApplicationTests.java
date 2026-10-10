package rw.ac.auca.transitdues;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Listener auto-startup is disabled here because RABBITMQ_URI isn't set in this test
 * environment; without it, the @RabbitListener containers would still start and
 * retry a broker connection in the background, making this test slow and noisy. The
 * context — including the RabbitMQ beans themselves — still loads and is verified.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class TransitDuesSpringBootApplicationTests {

    @Test
    void contextLoads() {
    }

}

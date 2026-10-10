package rw.ac.auca.transitdues.email;

import java.time.LocalDateTime;

public record EmailEvent(
        String to,
        String subject,
        String body,
        LocalDateTime occurredAt
) {

    public static final String SEND = "email.send";
}

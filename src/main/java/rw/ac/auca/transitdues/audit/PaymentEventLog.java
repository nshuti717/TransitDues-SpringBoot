package rw.ac.auca.transitdues.audit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "payment_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventLog {

    @Id
    private String id;

    private String eventType;

    private String duePaymentId;

    private String operatorId;

    private String operatorName;

    private BigDecimal amount;

    private String status;

    private String performedBy;

    @Indexed(direction = IndexDirection.DESCENDING)
    private LocalDateTime occurredAt;
}

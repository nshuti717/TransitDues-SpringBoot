package rw.ac.auca.transitdues.duepayment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import rw.ac.auca.transitdues.base.BaseEntity;
import rw.ac.auca.transitdues.operator.domain.Operator;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "due_payment", indexes = {
        @Index(name = "idx_due_payment_operator_id", columnList = "operator_id"),
        @Index(name = "idx_due_payment_date_paid", columnList = "date_paid"),
        @Index(name = "idx_due_payment_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DuePayment extends BaseEntity {

    @Column(nullable = false)
    @Positive
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    @Column(nullable = false)
    @PastOrPresent
    private LocalDate datePaid;

    @Column(nullable = false)
    @NotBlank
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", nullable = false)
    private Operator operator;
}

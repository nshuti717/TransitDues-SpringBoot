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
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import rw.ac.auca.transitdues.base.BaseEntity;
import rw.ac.auca.transitdues.operator.domain.Operator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A due is issued with status PENDING and a dueDate. It becomes OVERDUE if
 * still PENDING once dueDate has passed (see getEffectiveStatus, and the
 * scheduled job that persists the same transition), or PAID once a payment is
 * recorded against it (reference, paidAt, paymentMethod set at that point).
 *
 * The old "datePaid" column is gone from this mapping (dueDate/paidAt replace
 * it), but the physical database column is left in place for the legacy-row
 * migration runner to read directly - see LegacyDuePaymentMigrationRunner.
 */
@Entity
@Table(name = "due_payment", indexes = {
        @Index(name = "idx_due_payment_operator_id", columnList = "operator_id"),
        @Index(name = "idx_due_payment_status", columnList = "status"),
        @Index(name = "idx_due_payment_due_date", columnList = "due_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_due_payment_operator_type_due_date",
                columnNames = {"operator_id", "type", "due_date"})
})
@Getter
@Setter
@NoArgsConstructor
public class DuePayment extends BaseEntity {

    private static final ZoneId KIGALI_ZONE = ZoneId.of("Africa/Kigali");

    @Column(nullable = false)
    @Positive
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DuePaymentStatus status;

    /*
     * Not marked nullable = false: this column is new, and Hibernate's
     * ddl-auto=update would try to add it as NOT NULL in one step, which fails
     * on a non-empty table with no default. The @NotNull below still requires
     * it for every new submission through the web/REST layers; the startup
     * migration runner backfills it for rows that pre-date this column.
     */
    @NotNull
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Null until the due is paid. */
    @Column
    private String reference;

    /** Null until the due is paid. */
    @Column(name = "paid_at")
    private LocalDate paidAt;

    /** Null until the due is paid. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    /** Who issued this due (display name of the finance officer). */
    @Column(name = "issued_by")
    private String issuedBy;

    /**
     * When the operator started a payment attempt (online submission or a cash
     * request). Null until then; left in place (not cleared) once paid, as a
     * record of when the attempt began.
     */
    @Column(name = "submitted_at")
    private LocalDate submittedAt;

    /** Which finance officer confirmed a cash payment. Null otherwise. */
    @Column(name = "confirmed_by")
    private String confirmedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", nullable = false)
    private Operator operator;

    /**
     * The status to show/filter by right now, without waiting for the nightly
     * job: a PENDING due whose dueDate has already passed is treated as
     * OVERDUE even if the stored status column has not been flipped yet.
     */
    @Transient
    public DuePaymentStatus getEffectiveStatus() {
        if (status == DuePaymentStatus.PENDING && dueDate != null && dueDate.isBefore(LocalDate.now(KIGALI_ZONE))) {
            return DuePaymentStatus.OVERDUE;
        }
        return status;
    }
}

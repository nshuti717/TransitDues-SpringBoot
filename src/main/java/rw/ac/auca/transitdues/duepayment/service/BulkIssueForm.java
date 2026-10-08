package rw.ac.auca.transitdues.duepayment.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import rw.ac.auca.transitdues.duepayment.domain.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Bound from the bulk-issue form. Separate from the DuePayment entity, same
 * reasoning as RegisterForm: a form should never bind straight to an entity.
 */
@Getter
@Setter
public class BulkIssueForm {

    @NotNull(message = "Choose a payment type.")
    private PaymentType type;

    @NotNull(message = "Amount is required.")
    @Positive(message = "Amount must be greater than zero.")
    private BigDecimal amount;

    @NotNull(message = "Choose a due date.")
    private LocalDate dueDate;

    @NotNull(message = "Choose a scope.")
    private BulkIssueScope scope;

    /** Required only when scope is STAGE. */
    private UUID stageId;
}

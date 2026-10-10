package rw.ac.auca.transitdues.operator.domain;

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
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import rw.ac.auca.transitdues.base.BaseEntity;
import rw.ac.auca.transitdues.stage.domain.Stage;

import java.time.LocalDateTime;

@Entity
@Table(name = "operator", indexes = {
        @Index(name = "idx_operator_stage_id", columnList = "stage_id"),
        @Index(name = "idx_operator_approval_status", columnList = "approval_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Operator extends BaseEntity {

    @Column(nullable = false)
    @NotBlank
    private String fullName;

    @Column(nullable = false)
    @Pattern(regexp = "^(07[0-9]{8})$", message = "Phone number must be a valid Rwandan phone number (e.g. 07XXXXXXXX)")
    private String phoneNumber;

    @Column(nullable = false, unique = true)
    @NotBlank
    private String plateNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    /**
     * Not marked nullable = false: this column is new, and ddl-auto=update would
     * try to add it as NOT NULL in one step, which fails on a non-empty table
     * with no default. The Java-side default below covers every creation path
     * that doesn't explicitly set it (admin-created operators are trusted and
     * start ACTIVE); self-registration explicitly overrides this to
     * PENDING_APPROVAL. OperatorApprovalBackfillRunner backfills pre-existing
     * rows written before this column existed, so operators that were already
     * active keep working exactly as before.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    private ApprovalStatus approvalStatus = ApprovalStatus.ACTIVE;

    /** Who last approved/rejected/suspended/deactivated this operator. Null until a first action. */
    @Column(name = "approval_action_by")
    private String approvalActionBy;

    /** When that last approval-workflow action happened. Null until a first action. */
    @Column(name = "approval_action_at")
    private LocalDateTime approvalActionAt;

    /** Optional reason given for the last approval-workflow action. Null until a first action. */
    @Column(name = "approval_reason")
    private String approvalReason;
}

package rw.ac.auca.transitdues.operator.service;

import rw.ac.auca.transitdues.operator.domain.Operator;

import java.util.List;
import java.util.UUID;

public interface OperatorService {

    default Operator createOperator(Operator operator) {
        return createOperator(operator, null);
    }

    /**
     * Same checks as {@link #createOperator(Operator)} (stage exists, stage has
     * capacity), but lets callers with no authenticated principal of their own
     * (e.g. self-registration) supply who the audit log should credit.
     */
    Operator createOperator(Operator operator, String performedByOverride);

    Operator updateOperator(UUID id, Operator operator);

    void deleteOperator(UUID id);

    Operator findOperatorById(UUID id);

    List<Operator> findAllOperators();

    /** All operators except DEACTIVATED ones - what the normal Operators management list shows. */
    List<Operator> findActiveListOperators();

    /** Operators awaiting a Finance Officer/Admin review. */
    List<Operator> findPendingApprovalOperators();

    /** ACTIVE, stage-assigned operators - the only ones eligible for dues. */
    List<Operator> findEligibleActiveOperators();

    /**
     * Approves a PENDING_APPROVAL operator, confirming (or reassigning) a real,
     * capacity-available Stage. Only callable by ADMIN/FINANCE_OFFICER (enforced
     * at the controller).
     */
    Operator approveOperator(UUID id, UUID stageId, String reason);

    /** Refuses a PENDING_APPROVAL operator. Blocked from dues/payments afterwards. */
    Operator rejectOperator(UUID id, String reason);

    /** Temporarily blocks an ACTIVE operator from dues/payments. */
    Operator suspendOperator(UUID id, String reason);

    /**
     * Admin-only safe removal: soft-deletes the operator by flipping it to
     * DEACTIVATED and disabling its linked login (if any). The operator row and
     * every due/payment/audit record tied to it is left untouched - only
     * {@code deleteOperator} performs a real row delete, and only via the
     * legacy REST endpoint. Refuses to deactivate the operator profile linked
     * to {@code requesterIdentifier} itself (no self-deletion).
     */
    Operator deactivateOperator(UUID id, String reason, String requesterIdentifier);
}

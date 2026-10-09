package rw.ac.auca.transitdues.operator.domain;

/**
 * An operator's business approval state, independent of whether it has a
 * login account. Only ACTIVE operators are eligible to receive or pay dues
 * (see DuePaymentServiceImpl); PENDING_APPROVAL/REJECTED/SUSPENDED operators
 * may still sign in (if they have a verified account) but see a restricted
 * message on /portal instead of their dues (see PortalWebController).
 */
public enum ApprovalStatus {
    /** Self-registered, awaiting a Finance Officer/Admin review and stage confirmation. */
    PENDING_APPROVAL,
    /** Approved and assigned to a stage; eligible for dues and payments. */
    ACTIVE,
    /** Reviewed and refused; blocked from dues and payments. */
    REJECTED,
    /** Previously active, temporarily blocked from dues and payments by Finance/Admin. */
    SUSPENDED,
    /**
     * Removed by an Admin (see OperatorServiceImpl#deactivateOperator). The
     * operator row and all of its financial/audit history are kept - only its
     * login (if any) is disabled and it drops out of the normal operator
     * lists - so this is a soft delete, not a destructive one.
     */
    DEACTIVATED
}

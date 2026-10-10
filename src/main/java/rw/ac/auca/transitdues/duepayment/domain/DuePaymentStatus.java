package rw.ac.auca.transitdues.duepayment.domain;

public enum DuePaymentStatus {
    PENDING,
    /** Operator started an online payment; awaiting their confirmation step. */
    SUBMITTED,
    /** Operator asked to pay cash in person; awaiting finance confirmation. */
    CASH_PENDING,
    PAID,
    /** Simulated online payment did not succeed; operator may retry. */
    FAILED,
    OVERDUE
}

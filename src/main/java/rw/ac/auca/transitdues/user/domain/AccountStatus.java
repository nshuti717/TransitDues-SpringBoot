package rw.ac.auca.transitdues.user.domain;

public enum AccountStatus {
    /** Self-registered but the email has not been OTP-verified yet. */
    PENDING_VERIFICATION,
    ACTIVE
}

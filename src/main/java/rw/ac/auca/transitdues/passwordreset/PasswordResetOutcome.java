package rw.ac.auca.transitdues.passwordreset;

import rw.ac.auca.transitdues.otp.OtpVerificationResult;

/**
 * User-facing outcome of a reset attempt. Deliberately coarser than
 * {@link OtpVerificationResult}: NOT_FOUND (no such code) and an
 * already-vanished account both collapse into the same message as INCORRECT,
 * so a failed attempt never reveals whether an account exists for the email
 * typed.
 */
public enum PasswordResetOutcome {
    SUCCESS,
    INCORRECT_OR_EXPIRED,
    TOO_MANY_ATTEMPTS;

    public String userMessage() {
        return switch (this) {
            case SUCCESS -> "";
            case TOO_MANY_ATTEMPTS -> "Too many incorrect attempts for this code. Please request a new one.";
            case INCORRECT_OR_EXPIRED -> "That code is incorrect or has expired. Check it, or request a new one.";
        };
    }

    static PasswordResetOutcome fromOtpResult(OtpVerificationResult result) {
        return switch (result) {
            case VERIFIED -> SUCCESS;
            case TOO_MANY_ATTEMPTS -> TOO_MANY_ATTEMPTS;
            case INCORRECT, EXPIRED, NOT_FOUND -> INCORRECT_OR_EXPIRED;
        };
    }
}

package rw.ac.auca.transitdues.verification;

import rw.ac.auca.transitdues.otp.OtpVerificationResult;

/**
 * User-facing outcome of a verify attempt. Mirrors PasswordResetOutcome's
 * shape: NOT_FOUND and INCORRECT collapse into the same generic message, so a
 * failed attempt never reveals whether a pending registration exists for the
 * email typed.
 */
public enum AccountVerificationOutcome {
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

    static AccountVerificationOutcome fromOtpResult(OtpVerificationResult result) {
        return switch (result) {
            case VERIFIED -> SUCCESS;
            case TOO_MANY_ATTEMPTS -> TOO_MANY_ATTEMPTS;
            case INCORRECT, EXPIRED, NOT_FOUND -> INCORRECT_OR_EXPIRED;
        };
    }
}

package rw.ac.auca.transitdues.loginverification;

import rw.ac.auca.transitdues.otp.OtpVerificationResult;

/**
 * User-facing outcome of a login-OTP verify attempt. Same shape as
 * AccountVerificationOutcome/PasswordResetOutcome: incorrect, expired, and
 * not-found all collapse into one generic message, so a failed attempt never
 * reveals which of those actually happened.
 */
public enum LoginVerificationOutcome {
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

    static LoginVerificationOutcome fromOtpResult(OtpVerificationResult result) {
        return switch (result) {
            case VERIFIED -> SUCCESS;
            case TOO_MANY_ATTEMPTS -> TOO_MANY_ATTEMPTS;
            case INCORRECT, EXPIRED, NOT_FOUND -> INCORRECT_OR_EXPIRED;
        };
    }
}

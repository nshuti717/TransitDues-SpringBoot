package rw.ac.auca.transitdues.config;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown by {@link OtpGatedAuthenticationProvider} once a password has
 * already checked out correct, instead of returning an authenticated token.
 * From Spring Security's point of view this looks exactly like an
 * authentication failure - which is the point: nothing gets written to the
 * SecurityContext or session until the login OTP that follows is verified.
 * {@link LoginOtpRequiredFailureHandler} catches this specific type and sends
 * the browser to /verify-login instead of the generic /login?error.
 */
public class LoginOtpRequiredException extends AuthenticationException {

    private final String email;

    public LoginOtpRequiredException(String email) {
        super("Password verified; an OTP must be confirmed before sign-in completes.");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}

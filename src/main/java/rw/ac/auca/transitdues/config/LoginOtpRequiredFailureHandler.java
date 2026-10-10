package rw.ac.auca.transitdues.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A correct password that still needs {@link LoginOtpRequiredException}'s
 * second factor is routed to /verify-login; every other login failure (wrong
 * password, disabled/pending account) keeps the existing /login?error
 * behavior unchanged.
 */
@Component
public class LoginOtpRequiredFailureHandler implements AuthenticationFailureHandler {

    private final AuthenticationFailureHandler defaultFailureHandler = new SimpleUrlAuthenticationFailureHandler("/login?error");

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException, ServletException {
        if (exception instanceof LoginOtpRequiredException otpRequired) {
            String encodedEmail = UriUtils.encode(otpRequired.getEmail(), StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/verify-login?email=" + encodedEmail);
            return;
        }
        defaultFailureHandler.onAuthenticationFailure(request, response, exception);
    }
}

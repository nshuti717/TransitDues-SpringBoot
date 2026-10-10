package rw.ac.auca.transitdues.config;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import rw.ac.auca.transitdues.loginverification.LoginVerificationService;
import rw.ac.auca.transitdues.user.service.CustomUserDetailsService;

/**
 * Replaces the DaoAuthenticationProvider Spring Boot would otherwise
 * auto-configure from CustomUserDetailsService + PasswordEncoder (see the
 * comment this displaces in SecurityConfig) with one wrapping that exact same
 * provider - same UserDetailsService, same PasswordEncoder, nothing parallel
 * - and adding one more step: a correct password does not finish
 * authentication. It sends a LOGIN_VERIFY OTP and throws
 * {@link LoginOtpRequiredException} instead of returning the authenticated
 * token, so nothing is ever written to the SecurityContext or session until
 * that OTP is confirmed at /verify-login. A wrong password, disabled
 * account, or PENDING_VERIFICATION account still fails exactly as before -
 * those exceptions come out of the delegate before this class's logic ever
 * runs.
 */
@Component
public class OtpGatedAuthenticationProvider implements AuthenticationProvider {

    private final DaoAuthenticationProvider delegate;
    private final LoginVerificationService loginVerificationService;

    public OtpGatedAuthenticationProvider(CustomUserDetailsService customUserDetailsService,
                                           PasswordEncoder passwordEncoder,
                                           LoginVerificationService loginVerificationService) {
        this.delegate = new DaoAuthenticationProvider(customUserDetailsService);
        this.delegate.setPasswordEncoder(passwordEncoder);
        this.loginVerificationService = loginVerificationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Authentication passwordVerified = delegate.authenticate(authentication);

        String email = passwordVerified.getName();
        loginVerificationService.sendLoginCode(email);
        throw new LoginOtpRequiredException(email);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}

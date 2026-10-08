package rw.ac.auca.transitdues.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import rw.ac.auca.transitdues.user.service.CustomUserDetailsService;

/**
 * Signs a user in outside the normal login form - after an account-verification
 * or login OTP has just been confirmed, not after a password check Spring
 * Security itself performed. Loads the same {@link UserDetails} a real login
 * would via {@link CustomUserDetailsService}, puts it on the
 * {@link SecurityContextHolder}, and persists it to the session via
 * {@link HttpSessionSecurityContextRepository} - the standard
 * Spring-Security-documented way to authenticate someone programmatically.
 * Shared by every flow that needs this (account verification, login OTP) so it
 * is written once.
 */
@Component
@RequiredArgsConstructor
public class ProgrammaticAuthenticator {

    private final CustomUserDetailsService customUserDetailsService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public void signIn(String email, HttpServletRequest request, HttpServletResponse response) {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}

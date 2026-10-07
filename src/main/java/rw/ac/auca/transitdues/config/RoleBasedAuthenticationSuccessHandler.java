package rw.ac.auca.transitdues.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Routes a freshly authenticated user by role: an account whose only role is
 * OPERATOR lands on the operator portal, everyone else (ADMIN, FINANCE_OFFICER,
 * or an account holding OPERATOR alongside one of those) lands on the staff
 * dashboard. Used for both form login and Google OAuth2 login so there is a
 * single place deciding where a session ends up after signing in.
 */
@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Set<String> STAFF_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_FINANCE_OFFICER");

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        boolean isStaff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_AUTHORITIES::contains);

        String target = isStaff ? "/" : "/portal";
        response.sendRedirect(request.getContextPath() + target);
    }
}

package rw.ac.auca.transitdues.config;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Resolves a human-readable identity for the current user, shared between
 * {@link CurrentUserAdvice} (view display name) and the audit log, so both agree on
 * using the OAuth2 name/email rather than the OIDC subject id.
 */
public final class AuthenticatedUserResolver {

    private AuthenticatedUserResolver() {
    }

    public static String resolveDisplayName(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "";
        }

        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            Object name = oAuth2User.getAttribute("name");
            if (name != null) {
                return name.toString();
            }
            Object email = oAuth2User.getAttribute("email");
            if (email != null) {
                return email.toString();
            }
        }

        return authentication.getName();
    }

    /**
     * Resolves a stable identity for audit trails. A Google user is identified by
     * email rather than display name, since the name is not guaranteed unique and
     * the raw OIDC subject id is not human-readable.
     */
    public static String resolveAuditIdentity(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "";
        }

        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            Object email = oAuth2User.getAttribute("email");
            if (email != null) {
                return email.toString();
            }
        }

        return authentication.getName();
    }
}

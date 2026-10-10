package rw.ac.auca.transitdues.config;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the current user's display name and subtitle to every view, so templates
 * never need to inspect the Authentication/principal type themselves.
 */
@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("currentUserName")
    public String currentUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return AuthenticatedUserResolver.resolveDisplayName(authentication);
    }

    @ModelAttribute("currentUserSubtitle")
    public String currentUserSubtitle() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "";
        }

        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            Object email = oAuth2User.getAttribute("email");
            return email != null ? email.toString() : "";
        }

        return currentUserRoleLabel();
    }

    @ModelAttribute("currentUserRoleLabel")
    public String currentUserRoleLabel() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "No role assigned";
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return "Administrator";
            }
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_FINANCE_OFFICER".equals(authority.getAuthority())) {
                return "Finance Officer";
            }
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_OPERATOR".equals(authority.getAuthority())) {
                return "Operator";
            }
        }

        return "No role assigned";
    }

    @ModelAttribute("currentUserInitials")
    public String currentUserInitials() {
        String name = currentUserName();
        if (name == null || name.isBlank()) {
            return "";
        }

        String[] words = name.trim().split("\\s+");
        if (words.length >= 2) {
            return ("" + Character.toUpperCase(words[0].charAt(0)) + Character.toUpperCase(words[1].charAt(0)));
        }

        String singleWord = words[0];
        int atIndex = singleWord.indexOf('@');
        String localPart = atIndex >= 0 ? singleWord.substring(0, atIndex) : singleWord;
        if (localPart.isEmpty()) {
            return "";
        }

        return localPart.substring(0, Math.min(2, localPart.length())).toUpperCase();
    }
}

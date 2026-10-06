package rw.ac.auca.transitdues.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Roles for Google-authenticated users come from two configured email lists
 * (app.roles.admin-emails, app.roles.finance-emails) instead of a fixed role.
 */
@Component
public class OAuth2UserRoleMapper implements GrantedAuthoritiesMapper {

    private final Set<String> adminEmails;
    private final Set<String> financeEmails;

    public OAuth2UserRoleMapper(@Value("${app.roles.admin-emails:}") String adminEmails,
                                 @Value("${app.roles.finance-emails:}") String financeEmails) {
        this.adminEmails = toEmailSet(adminEmails);
        this.financeEmails = toEmailSet(financeEmails);
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);

        String email = resolveEmail(authorities);
        if (email != null) {
            if (adminEmails.contains(email)) {
                mapped.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            if (financeEmails.contains(email)) {
                mapped.add(new SimpleGrantedAuthority("ROLE_FINANCE_OFFICER"));
            }
        }

        return mapped;
    }

    private String resolveEmail(Collection<? extends GrantedAuthority> authorities) {
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidcUserAuthority) {
                String email = oidcUserAuthority.getIdToken().getEmail();
                if (email != null) {
                    return normalize(email);
                }
            } else if (authority instanceof OAuth2UserAuthority oAuth2UserAuthority) {
                Object email = oAuth2UserAuthority.getAttributes().get("email");
                if (email != null) {
                    return normalize(email.toString());
                }
            }
        }
        return null;
    }

    private Set<String> toEmailSet(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(commaSeparated.split(","))
                .map(this::normalize)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

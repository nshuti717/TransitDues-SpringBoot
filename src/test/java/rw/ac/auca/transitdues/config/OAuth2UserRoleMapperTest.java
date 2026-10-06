package rw.ac.auca.transitdues.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuth2UserRoleMapperTest {

    private static final SimpleGrantedAuthority ROLE_ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final SimpleGrantedAuthority ROLE_FINANCE_OFFICER = new SimpleGrantedAuthority("ROLE_FINANCE_OFFICER");

    @Test
    void adminEmailGetsRoleAdmin() {
        OAuth2UserRoleMapper mapper = new OAuth2UserRoleMapper("admin@example.com", "finance@example.com");

        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(Set.of(oidcAuthority("Admin@Example.com ")));

        assertTrue(mapped.contains(ROLE_ADMIN));
        assertFalse(mapped.contains(ROLE_FINANCE_OFFICER));
    }

    @Test
    void financeEmailGetsRoleFinanceOfficer() {
        OAuth2UserRoleMapper mapper = new OAuth2UserRoleMapper("admin@example.com", "finance@example.com");

        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(Set.of(oidcAuthority("finance@example.com")));

        assertTrue(mapped.contains(ROLE_FINANCE_OFFICER));
        assertFalse(mapped.contains(ROLE_ADMIN));
    }

    @Test
    void unknownEmailGetsNoRole() {
        OAuth2UserRoleMapper mapper = new OAuth2UserRoleMapper("admin@example.com", "finance@example.com");

        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(Set.of(oidcAuthority("stranger@example.com")));

        assertFalse(mapped.contains(ROLE_ADMIN));
        assertFalse(mapped.contains(ROLE_FINANCE_OFFICER));
    }

    private OidcUserAuthority oidcAuthority(String email) {
        OidcIdToken idToken = OidcIdToken.withTokenValue("fake-token")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .subject("fake-subject")
                .claim("email", email)
                .build();
        return new OidcUserAuthority(idToken);
    }
}

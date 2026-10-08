package rw.ac.auca.transitdues.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.user.domain.Role;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;
import rw.ac.auca.transitdues.user.service.UserAccountService;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2UserRoleMapperTest {

    private static final SimpleGrantedAuthority ROLE_ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final SimpleGrantedAuthority ROLE_FINANCE_OFFICER = new SimpleGrantedAuthority("ROLE_FINANCE_OFFICER");
    private static final SimpleGrantedAuthority ROLE_OPERATOR = new SimpleGrantedAuthority("ROLE_OPERATOR");

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    @Test
    void adminEmailWithNoExistingAccountGetsRoleAdmin() {
        OAuth2UserRoleMapper mapper = mapper();
        when(userAccountRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());

        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(Set.of(oidcAuthority("Admin@Example.com ")));

        assertTrue(mapped.contains(ROLE_ADMIN));
        assertFalse(mapped.contains(ROLE_FINANCE_OFFICER));
        verify(userAccountService, never()).save(any());
    }

    @Test
    void financeEmailWithNoExistingAccountGetsRoleFinanceOfficer() {
        OAuth2UserRoleMapper mapper = mapper();
        when(userAccountRepository.findByEmailIgnoreCase("finance@example.com")).thenReturn(Optional.empty());

        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(Set.of(oidcAuthority("finance@example.com")));

        assertTrue(mapped.contains(ROLE_FINANCE_OFFICER));
        assertFalse(mapped.contains(ROLE_ADMIN));
    }

    @Test
    void existingAccountUsesItsOwnDbRoleEvenIfOnAnEmailList() {
        OAuth2UserRoleMapper mapper = mapper();
        UserAccount operatorAccount = new UserAccount();
        operatorAccount.setRoles(Set.of(Role.OPERATOR));
        when(userAccountRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(operatorAccount));

        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(Set.of(oidcAuthority("admin@example.com")));

        assertTrue(mapped.contains(ROLE_OPERATOR));
        assertFalse(mapped.contains(ROLE_ADMIN));
        verify(userAccountService, never()).save(any());
    }

    @Test
    void unknownEmailIsAutoProvisionedAsARestrictedOperatorAccount() {
        OAuth2UserRoleMapper mapper = mapper();
        when(userAccountRepository.findByEmailIgnoreCase("stranger@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userAccountService.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });

        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(Set.of(oidcAuthority("stranger@example.com")));

        assertTrue(mapped.contains(ROLE_OPERATOR));
        assertFalse(mapped.contains(ROLE_ADMIN));
        assertFalse(mapped.contains(ROLE_FINANCE_OFFICER));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountService).save(captor.capture());
        UserAccount saved = captor.getValue();
        assertTrue(saved.getRoles().equals(Set.of(Role.OPERATOR)));
        assertTrue(saved.isEnabled());
        verify(auditLogService).record(eq("UserAccount"), anyString(), eq("CREATE"), anyString(),
                eq("stranger@example.com"));
    }

    private OAuth2UserRoleMapper mapper() {
        return new OAuth2UserRoleMapper("admin@example.com", "finance@example.com", userAccountRepository,
                userAccountService, passwordEncoder, auditLogService);
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

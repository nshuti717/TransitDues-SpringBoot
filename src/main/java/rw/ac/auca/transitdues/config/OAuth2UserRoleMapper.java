package rw.ac.auca.transitdues.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.user.domain.Role;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;
import rw.ac.auca.transitdues.user.service.UserAccountService;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Decides what role(s) a Google-authenticated user gets, in this order:
 * <ol>
 *     <li>An email matching an existing {@link UserAccount} uses that account's
 *     own DB-stored role(s) - this is how a seeded ADMIN/FINANCE_OFFICER account
 *     or an operator who registered normally can also sign in with Google.</li>
 *     <li>Otherwise, an email on the {@code app.roles.admin-emails} /
 *     {@code app.roles.finance-emails} lists gets that role, same as before -
 *     transient, no account is created.</li>
 *     <li>Otherwise, a restricted {@link UserAccount} with only
 *     {@link Role#OPERATOR} is auto-provisioned (no linked {@code Operator}, a
 *     random unusable password hash since this account is Google-login-only) so
 *     the person lands on {@code /portal} instead of a dead end. A Google login
 *     can never grant ADMIN or FINANCE_OFFICER this way - only an existing DB
 *     account or the configured email lists can.</li>
 * </ol>
 */
@Component
public class OAuth2UserRoleMapper implements GrantedAuthoritiesMapper {

    private final Set<String> adminEmails;
    private final Set<String> financeEmails;
    private final UserAccountRepository userAccountRepository;
    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public OAuth2UserRoleMapper(@Value("${app.roles.admin-emails:}") String adminEmails,
                                 @Value("${app.roles.finance-emails:}") String financeEmails,
                                 UserAccountRepository userAccountRepository,
                                 UserAccountService userAccountService,
                                 PasswordEncoder passwordEncoder,
                                 AuditLogService auditLogService) {
        this.adminEmails = toEmailSet(adminEmails);
        this.financeEmails = toEmailSet(financeEmails);
        this.userAccountRepository = userAccountRepository;
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);

        String email = resolveEmail(authorities);
        if (email == null) {
            return mapped;
        }

        Optional<UserAccount> existingAccount = userAccountRepository.findByEmailIgnoreCase(email);
        if (existingAccount.isPresent()) {
            existingAccount.get().getRoles()
                    .forEach(role -> mapped.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
            return mapped;
        }

        if (adminEmails.contains(email)) {
            mapped.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            return mapped;
        }
        if (financeEmails.contains(email)) {
            mapped.add(new SimpleGrantedAuthority("ROLE_FINANCE_OFFICER"));
            return mapped;
        }

        UserAccount provisioned = provisionRestrictedOperatorAccount(authorities, email);
        auditLogService.record("UserAccount", provisioned.getId().toString(), "CREATE",
                "Operator account auto-provisioned via Google login", email);
        mapped.add(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        return mapped;
    }

    private UserAccount provisionRestrictedOperatorAccount(Collection<? extends GrantedAuthority> authorities,
                                                             String email) {
        UserAccount account = new UserAccount();
        account.setFullName(resolveNameOrFallback(authorities, email));
        account.setEmail(email);
        // Google-login-only account: no form-login password is ever set or usable.
        account.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        account.setEnabled(true);
        account.setRoles(new LinkedHashSet<>(Set.of(Role.OPERATOR)));
        return userAccountService.save(account);
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

    private String resolveNameOrFallback(Collection<? extends GrantedAuthority> authorities, String fallback) {
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidcUserAuthority) {
                String name = oidcUserAuthority.getIdToken().getClaimAsString("name");
                if (name != null && !name.isBlank()) {
                    return name;
                }
            } else if (authority instanceof OAuth2UserAuthority oAuth2UserAuthority) {
                Object name = oAuth2UserAuthority.getAttributes().get("name");
                if (name != null && !name.toString().isBlank()) {
                    return name.toString();
                }
            }
        }
        return fallback;
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

package rw.ac.auca.transitdues.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import rw.ac.auca.transitdues.user.domain.Role;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;
import rw.ac.auca.transitdues.user.service.UserAccountService;

import java.util.Set;

/**
 * Creates the first ADMIN and FINANCE_OFFICER accounts on startup, if they do
 * not already exist, from environment-supplied credentials. Nothing here is
 * hardcoded: a missing email or password means that account is simply skipped,
 * so a fresh environment with no seed values configured starts with no
 * database-backed accounts at all (Google OAuth2 staff logins still work via
 * the admin/finance email lists).
 */
@Component
@RequiredArgsConstructor
public class SeedAccountsRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedAccountsRunner.class);

    private final UserAccountRepository userAccountRepository;
    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-email:}")
    private String adminEmail;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Value("${app.seed.finance-email:}")
    private String financeEmail;

    @Value("${app.seed.finance-password:}")
    private String financePassword;

    @Override
    public void run(ApplicationArguments args) {
        seedAccount("Administrator", adminEmail, adminPassword, Role.ADMIN, "SEED_ADMIN_EMAIL/SEED_ADMIN_PASSWORD");
        seedAccount("Finance Officer", financeEmail, financePassword, Role.FINANCE_OFFICER,
                "SEED_FINANCE_EMAIL/SEED_FINANCE_PASSWORD");
    }

    private void seedAccount(String fullName, String email, String password, Role role, String envVarNames) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("Skipping {} account seed: {} not set", role, envVarNames);
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (userAccountRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            return;
        }

        UserAccount account = new UserAccount();
        account.setFullName(fullName);
        account.setEmail(normalizedEmail);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setEnabled(true);
        account.setRoles(Set.of(role));
        userAccountService.save(account);
        log.info("Seeded {} account for {}", role, normalizedEmail);
    }
}

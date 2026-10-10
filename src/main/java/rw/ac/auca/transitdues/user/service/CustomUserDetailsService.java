package rw.ac.auca.transitdues.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.user.domain.AccountStatus;
import rw.ac.auca.transitdues.user.domain.Role;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.util.Optional;

/**
 * Loads a UserAccount by "email or phone": the login field accepts either, so a
 * staff member can sign in with their email while an operator (who may not
 * remember registering an email) can sign in with their phone number instead.
 * The resulting UserDetails always carries the account's canonical email as its
 * username, so Authentication.getName() is consistent regardless of which of the
 * two the person actually typed.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    @Override
    public UserDetails loadUserByUsername(String emailOrPhone) throws UsernameNotFoundException {
        if (emailOrPhone == null || emailOrPhone.isBlank()) {
            throw new UsernameNotFoundException("No account found for an empty username");
        }

        String normalized = emailOrPhone.trim();
        UserAccount account = findByEmailOrPhone(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for " + normalized));

        boolean pendingVerification = account.getStatus() == AccountStatus.PENDING_VERIFICATION;
        return User.builder()
                .username(account.getEmail())
                .password(account.getPasswordHash())
                .disabled(!account.isEnabled() || pendingVerification)
                .roles(account.getRoles().stream().map(Role::name).toArray(String[]::new))
                .build();
    }

    private Optional<UserAccount> findByEmailOrPhone(String emailOrPhone) {
        Optional<UserAccount> byEmail = userAccountRepository.findByEmailIgnoreCase(emailOrPhone);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        return userAccountRepository.findByOperatorPhoneNumber(emailOrPhone);
    }
}

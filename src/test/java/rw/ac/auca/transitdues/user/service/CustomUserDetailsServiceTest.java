package rw.ac.auca.transitdues.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import rw.ac.auca.transitdues.user.domain.AccountStatus;
import rw.ac.auca.transitdues.user.domain.Role;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void loadsAccountByEmail() {
        UserAccount account = account("jane@example.com", "hashed-password", Role.ADMIN);
        when(userAccountRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(account));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("jane@example.com");

        assertEquals("jane@example.com", userDetails.getUsername());
        assertEquals("hashed-password", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void loadsAccountByLinkedOperatorPhoneWhenNoEmailMatches() {
        UserAccount account = account("operator@example.com", "hashed-password", Role.OPERATOR);
        when(userAccountRepository.findByEmailIgnoreCase("0788111222")).thenReturn(Optional.empty());
        when(userAccountRepository.findByOperatorPhoneNumber("0788111222")).thenReturn(Optional.of(account));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("0788111222");

        // The principal's username is always the account's canonical email, even
        // when the person typed their phone number to sign in.
        assertEquals("operator@example.com", userDetails.getUsername());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OPERATOR")));
    }

    @Test
    void pendingVerificationAccountIsDisabled() {
        UserAccount account = account("pending@example.com", "hashed-password", Role.OPERATOR);
        account.setStatus(AccountStatus.PENDING_VERIFICATION);
        when(userAccountRepository.findByEmailIgnoreCase("pending@example.com")).thenReturn(Optional.of(account));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("pending@example.com");

        assertFalse(userDetails.isEnabled());
    }

    @Test
    void activeAccountIsNotDisabledByStatus() {
        UserAccount account = account("active@example.com", "hashed-password", Role.OPERATOR);
        account.setStatus(AccountStatus.ACTIVE);
        when(userAccountRepository.findByEmailIgnoreCase("active@example.com")).thenReturn(Optional.of(account));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("active@example.com");

        assertTrue(userDetails.isEnabled());
    }

    @Test
    void unknownEmailOrPhoneThrowsUsernameNotFound() {
        when(userAccountRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.findByOperatorPhoneNumber("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("nobody@example.com"));
    }

    private UserAccount account(String email, String passwordHash, Role role) {
        UserAccount account = new UserAccount();
        account.setFullName("Test User");
        account.setEmail(email);
        account.setPasswordHash(passwordHash);
        account.setEnabled(true);
        account.setRoles(Set.of(role));
        return account;
    }
}

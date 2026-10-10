package rw.ac.auca.transitdues.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rw.ac.auca.transitdues.exception.MultipleRolesException;
import rw.ac.auca.transitdues.user.domain.Role;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private UserAccountService userAccountService;

    @Test
    void savesAnAccountWithExactlyOneRole() {
        UserAccount account = accountWithRoles(Role.ADMIN);
        when(userAccountRepository.save(account)).thenReturn(account);

        UserAccount saved = userAccountService.save(account);

        assertEquals(account, saved);
    }

    @Test
    void rejectsAnAdminAccountThatAlsoHoldsOperator() {
        UserAccount account = accountWithRoles(Role.ADMIN, Role.OPERATOR);

        assertThrows(MultipleRolesException.class, () -> userAccountService.save(account));

        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void rejectsAnAccountWithNoRoles() {
        UserAccount account = accountWithRoles();

        assertThrows(MultipleRolesException.class, () -> userAccountService.save(account));

        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    private UserAccount accountWithRoles(Role... roles) {
        UserAccount account = new UserAccount();
        account.setFullName("Test User");
        account.setEmail("test.user@example.com");
        account.setPasswordHash("hashed-password");
        account.setEnabled(true);
        account.setRoles(Set.of(roles));
        return account;
    }
}

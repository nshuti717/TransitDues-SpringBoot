package rw.ac.auca.transitdues.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.user.domain.Role;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleConflictRepairRunnerTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private RoleConflictRepairRunner roleConflictRepairRunner;

    @Test
    void removesOperatorRoleAndUnlinksOperatorFromAConflictingAdminAccount() {
        UserAccount conflictingAdmin = new UserAccount();
        conflictingAdmin.setId(UUID.randomUUID());
        conflictingAdmin.setFullName("Conflicted Admin");
        conflictingAdmin.setEmail("conflicted.admin@example.com");
        conflictingAdmin.setPasswordHash("hashed-password");
        conflictingAdmin.setRoles(Set.of(Role.ADMIN, Role.OPERATOR));
        Operator operator = new Operator();
        operator.setId(UUID.randomUUID());
        conflictingAdmin.setOperator(operator);

        UserAccount cleanFinanceOfficer = new UserAccount();
        cleanFinanceOfficer.setId(UUID.randomUUID());
        cleanFinanceOfficer.setFullName("Clean Finance Officer");
        cleanFinanceOfficer.setEmail("finance@example.com");
        cleanFinanceOfficer.setPasswordHash("hashed-password");
        cleanFinanceOfficer.setRoles(Set.of(Role.FINANCE_OFFICER));

        when(userAccountRepository.findAll()).thenReturn(List.of(conflictingAdmin, cleanFinanceOfficer));

        roleConflictRepairRunner.run(null);

        assertEquals(Set.of(Role.ADMIN), conflictingAdmin.getRoles());
        assertFalse(conflictingAdmin.getRoles().contains(Role.OPERATOR));
        assertNull(conflictingAdmin.getOperator());

        ArgumentCaptor<UserAccount> savedCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository, times(1)).save(savedCaptor.capture());
        assertEquals("conflicted.admin@example.com", savedCaptor.getValue().getEmail());
    }

    @Test
    void leavesAccountsWithNoRoleConflictUntouched() {
        UserAccount operatorOnly = new UserAccount();
        operatorOnly.setId(UUID.randomUUID());
        operatorOnly.setFullName("Operator Only");
        operatorOnly.setEmail("operator.only@example.com");
        operatorOnly.setPasswordHash("hashed-password");
        operatorOnly.setRoles(Set.of(Role.OPERATOR));

        UserAccount adminOnly = new UserAccount();
        adminOnly.setId(UUID.randomUUID());
        adminOnly.setFullName("Admin Only");
        adminOnly.setEmail("admin.only@example.com");
        adminOnly.setPasswordHash("hashed-password");
        adminOnly.setRoles(Set.of(Role.ADMIN));

        when(userAccountRepository.findAll()).thenReturn(List.of(operatorOnly, adminOnly));

        roleConflictRepairRunner.run(null);

        assertTrue(operatorOnly.getRoles().contains(Role.OPERATOR));
        assertTrue(adminOnly.getRoles().contains(Role.ADMIN));
        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }
}

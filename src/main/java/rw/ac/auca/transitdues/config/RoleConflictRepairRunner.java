package rw.ac.auca.transitdues.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import rw.ac.auca.transitdues.user.domain.Role;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Repairs accounts left over from before the one-role-per-account rule existed:
 * any account holding OPERATOR together with ADMIN or FINANCE_OFFICER has the
 * OPERATOR role removed and its Operator profile unlinked (the Operator row
 * itself is kept, just no longer attached to that account).
 *
 * This saves through UserAccountRepository directly rather than
 * UserAccountService, on purpose: the whole point of this runner is to bring
 * legacy data back in line with the one-role rule, so it must not itself be
 * blocked by that same rule while doing so.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class RoleConflictRepairRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleConflictRepairRunner.class);
    private static final Set<Role> STAFF_ROLES = Set.of(Role.ADMIN, Role.FINANCE_OFFICER);

    private final UserAccountRepository userAccountRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (UserAccount account : userAccountRepository.findAll()) {
            Set<Role> roles = account.getRoles();
            boolean hasStaffRole = roles.stream().anyMatch(STAFF_ROLES::contains);
            boolean hasOperatorRole = roles.contains(Role.OPERATOR);

            if (hasStaffRole && hasOperatorRole) {
                Set<Role> fixedRoles = new LinkedHashSet<>(roles);
                fixedRoles.remove(Role.OPERATOR);
                account.setRoles(fixedRoles);
                account.setOperator(null);
                userAccountRepository.save(account);
                log.warn("Repaired account {}: removed OPERATOR role and unlinked its operator profile "
                        + "(now holds {})", account.getEmail(), fixedRoles);
            }
        }
    }
}

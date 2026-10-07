package rw.ac.auca.transitdues.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.exception.MultipleRolesException;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

/**
 * The single place a UserAccount is created or updated, so the one-role-per-
 * account rule cannot be bypassed by calling the repository directly. An ADMIN
 * or FINANCE_OFFICER account must never also hold OPERATOR (or any other
 * combination) - every account has exactly one role.
 */
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;

    public UserAccount save(UserAccount account) {
        int roleCount = account.getRoles() == null ? 0 : account.getRoles().size();
        if (roleCount != 1) {
            throw new MultipleRolesException("An account must have exactly one role, not " + roleCount + ".");
        }
        return userAccountRepository.save(account);
    }
}

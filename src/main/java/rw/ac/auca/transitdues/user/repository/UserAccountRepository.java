package rw.ac.auca.transitdues.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.ac.auca.transitdues.user.domain.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    /**
     * Looks up the account linked to an operator by that operator's phone number,
     * so a login can resolve "email or phone" without a separate phone column on
     * UserAccount itself.
     */
    Optional<UserAccount> findByOperatorPhoneNumber(String phoneNumber);
}

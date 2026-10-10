package rw.ac.auca.transitdues.user.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import rw.ac.auca.transitdues.base.BaseEntity;
import rw.ac.auca.transitdues.operator.domain.Operator;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount extends BaseEntity {

    @Column(nullable = false)
    @NotBlank
    private String fullName;

    @Column(nullable = false, unique = true)
    @NotBlank
    @Email
    private String email;

    @Column(nullable = false)
    @NotBlank
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Not marked nullable = false: this column is new, and ddl-auto=update would
     * try to add it as NOT NULL in one step, which fails on a non-empty table
     * with no default (same reasoning as DuePayment.dueDate). The Java-side
     * default below covers every creation path that doesn't explicitly set it
     * (seeded admin/finance, admin-created operator logins, Google
     * auto-provisioning); AccountStatusBackfillRunner backfills pre-existing
     * rows written before this column existed.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private AccountStatus status = AccountStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_account_role", joinColumns = @JoinColumn(name = "user_account_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new LinkedHashSet<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", unique = true)
    private Operator operator;
}

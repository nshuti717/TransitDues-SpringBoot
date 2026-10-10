package rw.ac.auca.transitdues.webcontroller;

import rw.ac.auca.transitdues.operator.domain.Operator;

/**
 * View-model pairing an Operator with the email of its linked login account
 * (if any) - Operator itself carries no email, only UserAccount does, and an
 * operator can exist with no login at all (admin-created with no email).
 * Used by the pending-approvals queue and the single-operator review page so
 * those templates never need to look the account up themselves.
 */
public record OperatorApprovalView(Operator operator, String email) {

    public String emailOrPlaceholder() {
        return email == null || email.isBlank() ? "No login account" : email;
    }
}

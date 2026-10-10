package rw.ac.auca.transitdues.exception;

/**
 * Thrown when a due or payment action is attempted against an operator who is
 * not an ACTIVE, stage-assigned operator (pending approval, rejected,
 * suspended, or deactivated).
 */
public class OperatorNotEligibleException extends RuntimeException {

    public OperatorNotEligibleException(String message) {
        super(message);
    }
}

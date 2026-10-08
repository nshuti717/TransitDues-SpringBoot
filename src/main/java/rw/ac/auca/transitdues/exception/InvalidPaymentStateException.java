package rw.ac.auca.transitdues.exception;

/**
 * Thrown when a payment action (pay online, confirm, cancel, request cash) is
 * attempted against a due that is not in a state that allows it - e.g.
 * paying a due that is already PAID, or confirming one that was never
 * submitted.
 */
public class InvalidPaymentStateException extends RuntimeException {
    public InvalidPaymentStateException(String message) {
        super(message);
    }
}

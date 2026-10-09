package rw.ac.auca.transitdues.exception;

/** Thrown when an Admin attempts to deactivate the operator profile linked to their own account. */
public class SelfActionNotAllowedException extends RuntimeException {

    public SelfActionNotAllowedException(String message) {
        super(message);
    }
}

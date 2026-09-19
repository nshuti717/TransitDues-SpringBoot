package rw.ac.auca.transitdues.exception;

public class DuePaymentNotFoundException extends RuntimeException {

    public DuePaymentNotFoundException(String message) {
        super(message);
    }
}

package rw.ac.auca.transitdues.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StageNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStageNotFoundException(StageNotFoundException ex,
                                                                        HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(OperatorNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOperatorNotFoundException(OperatorNotFoundException ex,
                                                                           HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuePaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDuePaymentNotFoundException(DuePaymentNotFoundException ex,
                                                                             HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(StageCapacityExceededException.class)
    public ResponseEntity<ErrorResponse> handleStageCapacityExceededException(StageCapacityExceededException ex,
                                                                                 HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicatePlateException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePlateException(DuplicatePlateException ex,
                                                                          HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateDuePaymentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateDuePaymentException(DuplicateDuePaymentException ex,
                                                                                HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaymentStateException(InvalidPaymentStateException ex,
                                                                                HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(OperatorNotEligibleException.class)
    public ResponseEntity<ErrorResponse> handleOperatorNotEligibleException(OperatorNotEligibleException ex,
                                                                               HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(SelfActionNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleSelfActionNotAllowedException(SelfActionNotAllowedException ex,
                                                                                 HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Thrown by the framework itself when nothing maps the request (a typo'd
     * URL, a removed route). More specific than the Exception.class handler
     * below, so this one wins and the response is a correct 404 instead of a
     * misleading 500 "Something went wrong".
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "No handler found for this request", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
                                                                                  HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Rethrown (not swallowed into JSON) so it propagates past this @RestControllerAdvice to
     * Spring Security's ExceptionTranslationFilter, which forwards it to the configured
     * AccessDeniedHandler (e.g. a styled Access Denied page for the Thymeleaf /web/** views).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDeniedException(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message,
                                                                HttpServletRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(errorResponse);
    }
}

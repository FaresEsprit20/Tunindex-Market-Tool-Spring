package com.tunindex.market_tool.collector.handlers;

import com.tunindex.market_tool.common.exception.CustomErrorMsg;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.exception.InvalidOperationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Turns the collector's domain exceptions into the status codes they mean.
 *
 * <p>The collector had no handler at all, so every one of these surfaced as a
 * 500: asking for a symbol that does not exist, or sending a page number of
 * zero, both reported "internal server error". The api service in front of it
 * has always had one, which is why the public API looked correct - the wrong
 * status was only visible to whoever called the collector directly, and the
 * tests that would have caught it could not start.
 *
 * <p>The distinction matters beyond tidiness. A 500 tells the caller we are
 * broken and the request is worth retrying; a 404 tells it the thing is not
 * there and retrying is pointless. The gateway's circuit breaker is one of
 * those callers, and a run of "not found" responses counted as failures can
 * trip it open and take out endpoints that were working perfectly.
 */
@RestControllerAdvice
@Slf4j
public class CollectorExceptionHandler {

    /** Asked for something we do not have. */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<CustomErrorMsg> handleNotFound(EntityNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getErrorCode(),
                exception.getMessage(), exception.getErrors());
    }

    /** Asked for something impossible - a page of zero, a size over the cap. */
    @ExceptionHandler(InvalidEntityException.class)
    public ResponseEntity<CustomErrorMsg> handleInvalidEntity(InvalidEntityException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getErrorCode(),
                exception.getMessage(), exception.getErrors());
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<CustomErrorMsg> handleInvalidOperation(InvalidOperationException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getErrorCode(),
                exception.getMessage(), exception.getErrors());
    }

    /**
     * A missing or wrong internal API key.
     *
     * <p>401, and deliberately without detail: the message would only tell
     * someone probing the endpoint whether they had the right shape of key.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<CustomErrorMsg> handleSecurity(SecurityException exception) {
        log.warn("Rejected internal call: {}", exception.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ErrorCodes.INVALID_PARAMETER,
                "Invalid or missing API key", List.of());
    }

    /**
     * Exceptions that already carry the status they mean.
     *
     * <p>Needed because of the catch-all below. The framework raises these for
     * things it has decided itself - an unmatched route, an unreadable body -
     * and without this handler the catch-all would take a perfectly correct
     * 404 and report it as 500. That is exactly what happened to the three
     * empty-path-variable cases: the route did not match, WebFlux said "not
     * found", and this class overruled it.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CustomErrorMsg> handleStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        return build(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status,
                ErrorCodes.INVALID_PARAMETER,
                exception.getReason() == null ? exception.getMessage() : exception.getReason(),
                List.of());
    }

    /**
     * Anything unrecognised really is a 500.
     *
     * <p>Logged with the stack trace, because this branch means something we
     * did not anticipate - and unlike the cases above, the caller's response
     * body will not say what it was.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomErrorMsg> handleUnexpected(Exception exception) {
        log.error("Unhandled exception in collector", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INVALID_PARAMETER,
                "An unexpected error occurred", List.of(exception.getMessage()));
    }

    private ResponseEntity<CustomErrorMsg> build(HttpStatus status, ErrorCodes code,
                                                 String message, List<String> errors) {
        CustomErrorMsg body = new CustomErrorMsg();
        body.setCode(code);
        body.setHttpCode(status.value());
        body.setMessage(message);
        body.setErrors(errors == null ? List.of() : errors);
        return new ResponseEntity<>(body, status);
    }
}

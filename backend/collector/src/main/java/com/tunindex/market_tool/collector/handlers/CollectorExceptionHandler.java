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

/**
 * The collector had no advice at all, so a bad request to an internal
 * endpoint (an unknown sector, an oversized page) came back as a bare 500
 * carrying a full stack trace — which the api module then surfaced verbatim
 * to the browser, internal host:port and filter chain included. These map
 * the same exception types the api module already maps, to the same codes,
 * so a validation failure reads as a 400 on both sides of the hop.
 */
@Slf4j
@RestControllerAdvice
public class CollectorExceptionHandler {

    @ExceptionHandler(InvalidEntityException.class)
    public ResponseEntity<CustomErrorMsg> handleInvalidEntity(InvalidEntityException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getErrorCode(), exception.getMessage(), exception.getErrors());
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<CustomErrorMsg> handleInvalidOperation(InvalidOperationException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getErrorCode(), exception.getMessage(), exception.getErrors());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<CustomErrorMsg> handleNotFound(EntityNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getErrorCode(), exception.getMessage(), exception.getErrors());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CustomErrorMsg> handleIllegalArgument(IllegalArgumentException exception) {
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PARAMETER, exception.getMessage(), java.util.List.of());
    }

    private ResponseEntity<CustomErrorMsg> build(HttpStatus status, ErrorCodes code, String message, java.util.List<String> errors) {
        log.warn("Collector request rejected [{}]: {}", status.value(), message);
        CustomErrorMsg body = new CustomErrorMsg();
        body.setCode(code);
        body.setHttpCode(status.value());
        body.setMessage(message);
        body.setErrors(errors);
        return new ResponseEntity<>(body, status);
    }
}

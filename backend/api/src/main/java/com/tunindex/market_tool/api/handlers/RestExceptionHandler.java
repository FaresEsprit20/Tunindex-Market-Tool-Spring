package com.tunindex.market_tool.api.handlers;

import com.tunindex.market_tool.common.exception.CustomErrorMsg;
import com.tunindex.market_tool.common.exception.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    // Add this new handler for validation exceptions
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CustomErrorMsg> handleConstraintViolationException(ConstraintViolationException exception, WebRequest webRequest) {
        final HttpStatus badRequest = HttpStatus.BAD_REQUEST;

        // Extract validation error messages
        List<String> errors = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());

        final CustomErrorMsg errorDto = new CustomErrorMsg();
        errorDto.setCode(ErrorCodes.INVALID_PARAMETER);
        errorDto.setHttpCode(badRequest.value());
        errorDto.setMessage("Validation failed: " + (errors.isEmpty() ? "Invalid parameter(s)" : errors.get(0)));
        errorDto.setErrors(errors);

        return new ResponseEntity<>(errorDto, badRequest);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<CustomErrorMsg> handleException(EntityNotFoundException exception, WebRequest webRequest) {
        final HttpStatus notFound = HttpStatus.NOT_FOUND;
        final CustomErrorMsg errorDto = new CustomErrorMsg();
        errorDto.setCode(exception.getErrorCode());
        errorDto.setHttpCode(notFound.value());
        errorDto.setMessage(exception.getMessage());
        errorDto.setErrors(exception.getErrors());
        return new ResponseEntity<>(errorDto, notFound);
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<CustomErrorMsg> handleException(InvalidOperationException exception, WebRequest webRequest) {
        final HttpStatus notFound = HttpStatus.BAD_REQUEST;
        final CustomErrorMsg errorDto = new CustomErrorMsg();
        errorDto.setCode(exception.getErrorCode());
        errorDto.setHttpCode(notFound.value());
        errorDto.setMessage(exception.getMessage());
        errorDto.setErrors(exception.getErrors());
        return new ResponseEntity<>(errorDto, notFound);
    }

    @ExceptionHandler(InvalidEntityException.class)
    public ResponseEntity<CustomErrorMsg> handleException(InvalidEntityException exception, WebRequest webRequest) {
        final HttpStatus badRequest = HttpStatus.BAD_REQUEST;
        final CustomErrorMsg errorDto = new CustomErrorMsg();
        errorDto.setCode(exception.getErrorCode());
        errorDto.setHttpCode(badRequest.value());
        errorDto.setMessage(exception.getMessage());
        errorDto.setErrors(exception.getErrors());
        return new ResponseEntity<>(errorDto, badRequest);
    }

    /**
     * Errors raised by a downstream service (the collector) reached the
     * browser as a 500 whose body was the whole WebClient stack trace —
     * leaking the internal host:port and filter chain, and turning a plain
     * "unknown sector" validation failure into an apparent server crash.
     * Pass the downstream status through and forward its JSON body, falling
     * back to a terse message when the body isn't the shared error shape.
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<CustomErrorMsg> handleDownstreamException(WebClientResponseException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        try {
            CustomErrorMsg downstream = exception.getResponseBodyAs(CustomErrorMsg.class);
            if (downstream != null && downstream.getMessage() != null) {
                downstream.setHttpCode(status.value());
                return new ResponseEntity<>(downstream, status);
            }
        } catch (Exception ignored) {
            // Body wasn't our error shape — fall through to the generic one.
        }

        CustomErrorMsg errorDto = new CustomErrorMsg();
        errorDto.setCode(ErrorCodes.INVALID_PARAMETER);
        errorDto.setHttpCode(status.value());
        errorDto.setMessage(status.is4xxClientError()
                ? "The request was rejected by the data service."
                : "The data service is unavailable. Please try again shortly.");
        errorDto.setErrors(List.of());
        return new ResponseEntity<>(errorDto, status);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity
                .badRequest()
                .body(ex.getMessage());
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<CustomErrorMsg> handleSQLException(SQLException exception, WebRequest webRequest) {
        final HttpStatus internalServerError = HttpStatus.INTERNAL_SERVER_ERROR;
        String rawMsg = exception.getMessage();
        String safeMsg;
        if (rawMsg != null && rawMsg.contains("Detail:")) {
            safeMsg = rawMsg.substring(rawMsg.indexOf("Detail:") + 7).trim();
        } else {
            safeMsg = "A database error occurred while processing your request.";
        }
        final CustomErrorMsg errorDto = CustomErrorMsg.builder()
                .code(ErrorCodes.DATABASE_ERROR)
                .httpCode(internalServerError.value())
                .message("Database error occurred: " + safeMsg)
                .build();
        return new ResponseEntity<>(errorDto, internalServerError);
    }


    @ExceptionHandler(RecaptchaException.class)
    public ResponseEntity<CustomErrorMsg> handleException(RecaptchaException exception, WebRequest webRequest) {
        final HttpStatus badRequest = HttpStatus.BAD_REQUEST;
        final CustomErrorMsg errorDto = new CustomErrorMsg();
        errorDto.setCode(exception.getErrorCode());
        errorDto.setHttpCode(badRequest.value());
        errorDto.setMessage(exception.getMessage());
        errorDto.setErrors(exception.getErrors());
        return new ResponseEntity<>(errorDto, badRequest);
    }



}
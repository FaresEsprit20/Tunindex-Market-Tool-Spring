package com.tunindex.market_tool.common.handlers;

import com.tunindex.market_tool.common.exception.CustomErrorMsg;
import com.tunindex.market_tool.common.exception.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
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
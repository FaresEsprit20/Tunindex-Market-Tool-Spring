package com.tunindex.market_tool.api.handlers;

import com.tunindex.market_tool.common.exception.CustomErrorMsg;
import com.tunindex.market_tool.common.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.util.Collections;

@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

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


    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomErrorMsg> handleAllUncaughtException(Exception ex) {
        // Always log the full error
        logger.error("Unhandled exception occurred", ex);

        CustomErrorMsg errorDto = new CustomErrorMsg();
        errorDto.setCode(ErrorCodes.GENERIC_ERROR);
        errorDto.setHttpCode(HttpStatus.INTERNAL_SERVER_ERROR.value());

        errorDto.setMessage(" Error: " + ex.getClass().getName());
        errorDto.setErrors(Collections.singletonList(getFilteredStackTrace(ex)));


        return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String getFilteredStackTrace(Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append("\n");

        // Filter out common noisy packages
        String[] excludedPackages = {
                "org.springframework.",
                "sun.reflect.",
                "java.lang.reflect.",
                "javax.servlet.",
                "org.apache.tomcat.",
                "org.apache.catalina.",
                "com.sun.",
                "jdk.internal.reflect."
        };

        int maxFrames = 10; // Limit number of frames to show
        int shownFrames = 0;

        for (StackTraceElement element : ex.getStackTrace()) {
            // Skip framework/internal stack traces
            boolean isExcluded = false;
            for (String pkg : excludedPackages) {
                if (element.getClassName().startsWith(pkg)) {
                    isExcluded = true;
                    break;
                }
            }

            if (!isExcluded) {
                sb.append("\tat ")
                        .append(element.getClassName())
                        .append(".")
                        .append(element.getMethodName())
                        .append("(")
                        .append(element.getFileName())
                        .append(":")
                        .append(element.getLineNumber())
                        .append(")\n");

                shownFrames++;
                if (shownFrames >= maxFrames) {
                    break;
                }
            }
        }

        // Indicate if stack trace was truncated
        if (shownFrames < ex.getStackTrace().length) {
            sb.append("\t... ").append(ex.getStackTrace().length - shownFrames)
                    .append(" more frames filtered\n");
        }

        // Include root cause if exists
        if (ex.getCause() != null) {
            sb.append("Caused by: ")
                    .append(ex.getCause().getClass().getName())
                    .append(": ")
                    .append(ex.getCause().getMessage())
                    .append("\n");
        }

        return sb.toString();
    }

}

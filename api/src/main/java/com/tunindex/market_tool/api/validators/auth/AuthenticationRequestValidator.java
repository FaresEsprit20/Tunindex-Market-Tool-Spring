package com.tunindex.market_tool.api.validators.auth;

import com.tunindex.market_tool.api.config.security.sanitizers.InputSanitizer;
import com.tunindex.market_tool.api.dto.two_factor.AuthenticationRequestMfoDto;
import com.tunindex.market_tool.api.exception.ErrorCodes;
import com.tunindex.market_tool.api.exception.InvalidEntityException;
import com.tunindex.market_tool.api.utils.constants.FieldsValidation;

import java.util.ArrayList;
import java.util.List;

public class AuthenticationRequestValidator {

    public static void validate(AuthenticationRequestMfoDto request) {
        // Validate login (username)
        List<String> errors = new ArrayList<String>();
        
        // Sanitize all string fields
        request.setLogin(InputSanitizer.fullSanitize(request.getLogin()));
        request.setPassword(InputSanitizer.fullSanitize(request.getPassword()));
        
        if (request.getLogin() == null || request.getLogin().trim().isEmpty()) {
            errors.add("Login (username) must not be null or empty");
            throw new InvalidEntityException("Login (username) must not be null or empty", ErrorCodes.USER_NOT_VALID, errors);
        }
        if (request.getLogin().length() < FieldsValidation.MIN_LOGIN_LENGTH ||
                request.getLogin().length() > FieldsValidation.MAX_LOGIN_LENGTH) {
            errors.add("Login (username) must be between " + FieldsValidation.MIN_LOGIN_LENGTH +
                    " and " + FieldsValidation.MAX_LOGIN_LENGTH + " characters");
            throw new InvalidEntityException("Login (username) must be between " + FieldsValidation.MIN_LOGIN_LENGTH +
                    " and " + FieldsValidation.MAX_LOGIN_LENGTH + " characters", ErrorCodes.USER_NOT_VALID, errors);
        }
        if (!request.getLogin().matches(FieldsValidation.EMAIL_REGEX)) {
            errors.add("Login (email) must be a valid email address");
            throw new InvalidEntityException("User Email must be Valid ", ErrorCodes.USER_NOT_VALID, errors );
        }
        // Validate password
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            errors.add("Password (password) must not be null or empty");
            throw new InvalidEntityException("Password must not be null or empty",ErrorCodes.USER_NOT_VALID, errors);
        }
        if (request.getPassword().length() < FieldsValidation.MIN_PASSWORD_LENGTH ||
                request.getPassword().length() > FieldsValidation.MAX_PASSWORD_LENGTH) {
            errors.add("Password must be between " + FieldsValidation.MIN_PASSWORD_LENGTH +
                    " and " + FieldsValidation.MAX_PASSWORD_LENGTH + " characters");
            throw new InvalidEntityException("Password must be between " + FieldsValidation.MIN_PASSWORD_LENGTH +
                    " and " + FieldsValidation.MAX_PASSWORD_LENGTH + " characters", ErrorCodes.USER_NOT_VALID, errors);
        }
        if (!request.getPassword().matches(FieldsValidation.PASSWORD_REGEX)) {
            errors.add("Password must contain at least one uppercase letter, one lowercase letter, " +
                    "one number, and one special character");
            throw new InvalidEntityException("Password must contain at least one uppercase letter, one lowercase letter, " +
                    "one number, and one special character", ErrorCodes.USER_NOT_VALID, errors);
        }

        // Default rememberMe to false if not provided
        if (request.getRememberMe() == null) {
            request.setRememberMe(false);
        }

    }


}

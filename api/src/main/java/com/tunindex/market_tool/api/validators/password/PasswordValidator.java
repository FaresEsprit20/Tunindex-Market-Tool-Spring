package com.tunindex.market_tool.api.validators.password;

import com.tunindex.market_tool.api.config.security.sanitizers.InputSanitizer;
import com.tunindex.market_tool.api.utils.constants.FieldsValidation;

import java.util.ArrayList;
import java.util.List;

public class PasswordValidator {

    // Validate password
    public static List<String> validate(String password) {
        List<String> errors = new ArrayList<>();
        
        // Sanitize password
        password = InputSanitizer.fullSanitize(password);
        
        if (password == null || password.trim().isEmpty()) {
            errors.add("Password (password) must not be null or empty");
//            throw new InvalidEntityException("Password must not be null or empty", ErrorCodes.USER_NOT_VALID, errors);
        }else if (password.length() < FieldsValidation.MIN_PASSWORD_LENGTH ||
                password.length() > FieldsValidation.MAX_PASSWORD_LENGTH) {
            errors.add("Password must be between " + FieldsValidation.MIN_PASSWORD_LENGTH +
                    " and " + FieldsValidation.MAX_PASSWORD_LENGTH + " characters");
//            throw new InvalidEntityException("Password must be between " + FieldsValidation.MIN_PASSWORD_LENGTH +
//                    " and " + FieldsValidation.MAX_PASSWORD_LENGTH + " characters", ErrorCodes.USER_NOT_VALID, errors);
       }
        assert password != null;
        if (!password.matches(FieldsValidation.PASSWORD_REGEX)) {
            errors.add("Password must contain at least one uppercase letter, one lowercase letter, " +
                    "one number, and one special character");
//            throw new InvalidEntityException("Password must contain at least one uppercase letter, one lowercase letter, " +
//                    "one number, and one special character", ErrorCodes.USER_NOT_VALID, errors);
        }
        return errors;
    }

}

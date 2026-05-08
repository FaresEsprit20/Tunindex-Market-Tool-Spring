package com.tunindex.market_tool.common.validators.email;

import com.tunindex.market_tool.common.config.security.sanitizers.InputSanitizer;
import com.tunindex.market_tool.common.utils.constants.FieldsValidation;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EmailValidator {

    public static List<String> validate(String email) {
        List<String> errors = new ArrayList<>();

        if (email == null) {
            errors.add("Email field is required");
            return errors;
        }

        // Sanitize email
        email = InputSanitizer.fullSanitize(email);

        // Email Validation
        if (!StringUtils.hasLength(email) ||
                !Pattern.matches(FieldsValidation.EMAIL_REGEX, email)) {
            errors.add("Email is invalid: it must be a valid email format.");
        }

        return errors;
    }


}

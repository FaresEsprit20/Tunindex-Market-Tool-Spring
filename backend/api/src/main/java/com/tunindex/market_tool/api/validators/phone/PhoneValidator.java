package com.tunindex.market_tool.api.validators.phone;

import com.tunindex.market_tool.api.config.security.sanitizers.InputSanitizer;
import com.tunindex.market_tool.common.utils.constants.FieldsValidation;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PhoneValidator {

    public static List<String> validate(String phone) {

        List<String> errors = new ArrayList<>();

        if (phone == null) {
            errors.add("Phone field is required");
            return errors;
        }

        // Sanitize phone
        phone = InputSanitizer.fullSanitize(phone);

        // Phone Number Validation
        if (!StringUtils.hasLength(phone) ||
                !Pattern.matches(FieldsValidation.PHONE_NUMBER_REGEX, phone)) {
            errors.add("Phone Number is invalid");
        }
        return errors;
    }

}

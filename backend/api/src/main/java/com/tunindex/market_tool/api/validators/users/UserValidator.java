package com.tunindex.market_tool.api.validators.users;

import com.tunindex.market_tool.api.config.security.sanitizers.InputSanitizer;
import com.tunindex.market_tool.api.dto.user.UserDto;
import com.tunindex.market_tool.common.utils.constants.FieldsValidation;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class UserValidator {

    public static List<String> validate(UserDto userDto) {
        List<String> errors = new ArrayList<>();

        if (userDto == null) {
            errors.add("First Name field is required");
            errors.add("Last Name field is required");
            errors.add("Email field is required");
            errors.add("Birth Date field is required");
            errors.add("Password field is required");
            errors.add("At least one Role is required");
            return errors;
        }

        // Sanitize all string fields
        userDto.setFirstName(InputSanitizer.fullSanitize(userDto.getFirstName()));
        userDto.setLastName(InputSanitizer.fullSanitize(userDto.getLastName()));
        userDto.setEmail(InputSanitizer.fullSanitize(userDto.getEmail()));
        userDto.setUsername(InputSanitizer.fullSanitize(userDto.getUsername()));
        userDto.setPassword(InputSanitizer.fullSanitize(userDto.getPassword()));
        userDto.setPhoto(InputSanitizer.fullSanitize(userDto.getPhoto()));

        // First Name Validation
        if (!StringUtils.hasLength(userDto.getFirstName()) ||
                userDto.getFirstName().length() < FieldsValidation.MIN_FIRST_NAME_LENGTH ||
                userDto.getFirstName().length() > FieldsValidation.MAX_FIRST_NAME_LENGTH ||
                !Pattern.matches(FieldsValidation.FIRST_NAME_REGEX, userDto.getFirstName())) {
            errors.add("First Name is invalid: it must be between " + FieldsValidation.MIN_FIRST_NAME_LENGTH + " and " + FieldsValidation.MAX_FIRST_NAME_LENGTH +
                    " characters and match the pattern " + FieldsValidation.FIRST_NAME_REGEX);
        }

        // Last Name Validation
        if (!StringUtils.hasLength(userDto.getLastName()) ||
                userDto.getLastName().length() < FieldsValidation.MIN_LAST_NAME_LENGTH ||
                userDto.getLastName().length() > FieldsValidation.MAX_LAST_NAME_LENGTH ||
                !Pattern.matches(FieldsValidation.LAST_NAME_REGEX, userDto.getLastName())) {
            errors.add("Last Name is invalid: it must be between " + FieldsValidation.MIN_LAST_NAME_LENGTH + " and " + FieldsValidation.MAX_LAST_NAME_LENGTH +
                    " characters and match the pattern " + FieldsValidation.LAST_NAME_REGEX);
        }

        // Email Validation
        if (!StringUtils.hasLength(userDto.getEmail()) ||
                !Pattern.matches(FieldsValidation.EMAIL_REGEX, userDto.getEmail())) {
            errors.add("Email is invalid: it must be a valid email format.");
        }

        // Username Validation (optional — only checked when provided)
        if (StringUtils.hasLength(userDto.getUsername()) &&
                (userDto.getUsername().length() < FieldsValidation.MIN_USERNAME_LENGTH ||
                        userDto.getUsername().length() > FieldsValidation.MAX_USERNAME_LENGTH ||
                        !Pattern.matches(FieldsValidation.USERNAME_REGEX, userDto.getUsername()))) {
            errors.add("Username is invalid: it must be between " + FieldsValidation.MIN_USERNAME_LENGTH + " and " + FieldsValidation.MAX_USERNAME_LENGTH +
                    " characters and contain only letters, digits, underscores, dots or hyphens.");
        }

        // Birth Date Validation
        if (userDto.getBirthDate() == null) {
            errors.add("Birth Date is required.");
        }

        // Password Validation
        if (!StringUtils.hasLength(userDto.getPassword()) ||
                userDto.getPassword().length() < FieldsValidation.MIN_PASSWORD_LENGTH ||
                userDto.getPassword().length() > FieldsValidation.MAX_PASSWORD_LENGTH ||
                !Pattern.matches(FieldsValidation.PASSWORD_REGEX, userDto.getPassword())) {
            errors.add("Password is invalid: it must be between " + FieldsValidation.MIN_PASSWORD_LENGTH + " and " + FieldsValidation.MAX_PASSWORD_LENGTH +
                    " characters long, and must contain:\n" +
                    "- At least one lowercase letter\n" +
                    "- At least one uppercase letter\n" +
                    "- At least one digit\n" +
                    "- At least one special character (e.g., @$!%*?&-)");
        }


        // Photo Validation (if provided)
//        if (StringUtils.hasLength(userDto.getPhoto())
//              //  && !Pattern.matches(FieldsValidation.PHOTO_URL_REGEX, userDto.getPhoto())
//        ) {
//            errors.add("Photo URL is invalid: it must match the valid URL format.");
//        }

        // Roles Validation: Ensure at least one role is provided
        if (userDto.getRoles() == null || userDto.getRoles().isEmpty()) {
            errors.add("At least one Role is required.");
        }
        // Optionally, you can validate each role with a RolesValidator if available.
        return errors;
    }


}


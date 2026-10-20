package com.tunindex.market_tool.api.validators.users;

import com.tunindex.market_tool.api.dto.user.UserUpdateDto;
import com.tunindex.market_tool.common.utils.constants.FieldsValidation;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


public class UserUpdateValidator {

    public static List<String> validate(UserUpdateDto userDto) {
        List<String> errors = new ArrayList<>();

        if (userDto == null) {
            errors.add("Photo field is required");
            errors.add("Address ID field is required");
            return errors;
        }

        if (userDto.getPhoto() == null) {
            errors.add("Photo is required.");
        }

        if (userDto.getAddress() == null) {
            errors.add("Address is required.");
        }

        if (!StringUtils.hasLength(userDto.getFirstName()) ||
                userDto.getFirstName().length() < FieldsValidation.MIN_FIRST_NAME_LENGTH ||
                userDto.getFirstName().length() > FieldsValidation.MAX_FIRST_NAME_LENGTH ||
                !Pattern.matches(FieldsValidation.FIRST_NAME_REGEX, userDto.getFirstName())) {
            errors.add("First Name is invalid: it must be between " + FieldsValidation.MIN_FIRST_NAME_LENGTH + " and " + FieldsValidation.MAX_FIRST_NAME_LENGTH +
                    " characters and match the pattern " + FieldsValidation.FIRST_NAME_REGEX);
        }

        if (!StringUtils.hasLength(userDto.getLastName()) ||
                userDto.getLastName().length() < FieldsValidation.MIN_LAST_NAME_LENGTH ||
                userDto.getLastName().length() > FieldsValidation.MAX_LAST_NAME_LENGTH ||
                !Pattern.matches(FieldsValidation.LAST_NAME_REGEX, userDto.getLastName())) {
            errors.add("Last Name is invalid: it must be between " + FieldsValidation.MIN_LAST_NAME_LENGTH + " and " + FieldsValidation.MAX_LAST_NAME_LENGTH +
                    " characters and match the pattern " + FieldsValidation.LAST_NAME_REGEX);
        }

        // Optionally, you can validate each role with a RolesValidator if available.
        return errors;
    }


}


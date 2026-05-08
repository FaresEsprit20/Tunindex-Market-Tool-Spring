package com.tunindex.market_tool.common.validators.users;

import com.tunindex.market_tool.common.dto.user.UserUpdateDto;

import java.util.ArrayList;
import java.util.List;


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

        // Optionally, you can validate each role with a RolesValidator if available.
        return errors;
    }


}


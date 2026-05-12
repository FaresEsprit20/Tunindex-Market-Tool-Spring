package com.tunindex.market_tool.recaptcha.service;

import com.tunindex.market_tool.recaptcha.dto.UserRole;

import java.util.List;

public interface ApiServiceClient {

    List<UserPhoneDto> getAllPhoneNumbers();

    List<UserPhoneDto> getPhoneNumbersByRole(UserRole role);

    UserPhoneDto getPhoneNumberByEmail(String email);

    UserPhoneDto getPhoneNumberByUserId(Long userId);
}
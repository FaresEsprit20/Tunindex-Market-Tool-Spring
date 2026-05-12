package com.tunindex.market_tool.sms.service;

import com.tunindex.market_tool.sms.dto.UserPhoneDto;
import com.tunindex.market_tool.sms.dto.UserRole;

import java.util.List;

public interface ApiServiceClient {

    List<UserPhoneDto> getAllPhoneNumbers();

    List<UserPhoneDto> getPhoneNumbersByRole(UserRole role);

    UserPhoneDto getPhoneNumberByEmail(String email);

    UserPhoneDto getPhoneNumberByUserId(Long userId);
}
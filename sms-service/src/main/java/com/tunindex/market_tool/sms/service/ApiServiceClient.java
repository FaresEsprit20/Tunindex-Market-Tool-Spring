package com.tunindex.market_tool.sms.service;

import com.tunindex.market_tool.sms.dto.UserRole;

import java.util.List;

public interface ApiServiceClient {
    List<UserEmailDto> getAllUserEmails();

    List<UserEmailDto> getUserEmailsByRole(UserRole role);
}

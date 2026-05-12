package com.tunindex.market_tool.mailing.service;

import com.tunindex.market_tool.mailing.dto.UserEmailDto;
import com.tunindex.market_tool.mailing.dto.UserRole;

import java.util.List;

public interface ApiServiceClient {
    List<UserEmailDto> getAllUserEmails();

    List<UserEmailDto> getUserEmailsByRole(UserRole role);
}

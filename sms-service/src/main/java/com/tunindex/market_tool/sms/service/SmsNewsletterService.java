package com.tunindex.market_tool.sms.service;


import com.tunindex.market_tool.common.exception.InvalidPhoneNumberException;
import com.tunindex.market_tool.common.exception.SmsServiceException;
import com.tunindex.market_tool.sms.dto.UserRole;

public interface SmsNewsletterService {

    void sendSmsToAllUsers(String message) throws SmsServiceException, InvalidPhoneNumberException;

    void sendSmsToUsersByRole(UserRole userRole, String message) throws SmsServiceException, InvalidPhoneNumberException;

    void sendSmsToUser(String phoneNumber, String message) throws SmsServiceException, InvalidPhoneNumberException;

}

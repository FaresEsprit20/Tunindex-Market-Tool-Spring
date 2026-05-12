package com.tunindex.market_tool.sms.service;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.exception.InvalidPhoneNumberException;
import com.tunindex.market_tool.common.exception.SmsServiceException;
import com.tunindex.market_tool.sms.dto.UserPhoneDto;
import com.tunindex.market_tool.sms.dto.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsNewsletterServiceImpl implements SmsNewsletterService {

    private final SmsService smsService;
    private final ApiServiceClient apiServiceClient;

    @Override
    public void sendSmsToAllUsers(String message) throws SmsServiceException, InvalidPhoneNumberException {
        log.info("Sending SMS to ALL users");
        List<UserPhoneDto> users = apiServiceClient.getAllPhoneNumbers();

        for (UserPhoneDto user : users) {
            try {
                smsService.sendSms(user.getPhoneNumber(), message);
                log.debug("SMS sent to: {}", user.getPhoneNumber());
            } catch (Exception e) {
                log.error("Failed to send SMS to {}: {}", user.getPhoneNumber(), e.getMessage());
            }
        }
    }

    @Override
    public void sendSmsToUsersByRole(UserRole userRole, String message) throws SmsServiceException, InvalidPhoneNumberException {
        log.info("Sending SMS to users with role: {}", userRole);
        List<UserPhoneDto> users = apiServiceClient.getPhoneNumbersByRole(userRole);

        for (UserPhoneDto user : users) {
            try {
                smsService.sendSms(user.getPhoneNumber(), message);
                log.debug("SMS sent to: {}", user.getPhoneNumber());
            } catch (Exception e) {
                log.error("Failed to send SMS to {}: {}", user.getPhoneNumber(), e.getMessage());
            }
        }
    }

    @Override
    public void sendSmsToUser(String phoneNumber, String message) throws SmsServiceException, InvalidPhoneNumberException {
        log.info("Sending SMS to single user: {}", phoneNumber);

        // Validate the phone number before sending SMS
        List<String> errors = new ArrayList<>();
        if (!isValidPhoneNumber(phoneNumber)) {
            errors.add("Invalid phone number");
            throw new InvalidEntityException("Invalid phone number format.", ErrorCodes.INVALID_PHONE_NUMBER, errors);
        }

        smsService.sendSms(phoneNumber, message);
        log.info("SMS sent successfully to: {}", phoneNumber);
    }

    @Override
    public void sendSmsToUserByEmail(String email, String message) throws SmsServiceException, InvalidPhoneNumberException {
        log.info("Sending SMS to user by email: {}", email);

        UserPhoneDto user = apiServiceClient.getPhoneNumberByEmail(email);

        if (user == null || user.getPhoneNumber() == null) {
            log.error("No phone number found for email: {}", email);
            List<String> errors = new ArrayList<>();
            errors.add("No phone number found for email: " + email);
            throw new InvalidEntityException("User phone number not found", ErrorCodes.USER_NOT_FOUND, errors);
        }

        sendSmsToUser(user.getPhoneNumber(), message);
    }

    @Override
    public void sendSmsToUserById(Long userId, String message) throws SmsServiceException, InvalidPhoneNumberException {
        log.info("Sending SMS to user by ID: {}", userId);

        UserPhoneDto user = apiServiceClient.getPhoneNumberByUserId(userId);

        if (user == null || user.getPhoneNumber() == null) {
            log.error("No phone number found for user ID: {}", userId);
            List<String> errors = new ArrayList<>();
            errors.add("No phone number found for user ID: " + userId);
            throw new InvalidEntityException("User phone number not found", ErrorCodes.USER_NOT_FOUND, errors);
        }

        sendSmsToUser(user.getPhoneNumber(), message);
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        try {
            // Try to parse with default region (tunisia)
            Phonenumber.PhoneNumber number = phoneNumberUtil.parse(phoneNumber, "TN");
            return phoneNumberUtil.isValidNumber(number);
        } catch (Exception e) {
            log.warn("Invalid phone number format: {}", phoneNumber);
            return false;
        }
    }
}
package com.tunindex.market_tool.sms.service;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.exception.InvalidPhoneNumberException;
import com.tunindex.market_tool.common.exception.SmsServiceException;
import com.tunindex.market_tool.sms.dto.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SmsNewsletterServiceImpl implements SmsNewsletterService {

    private final SmsService smsService;
    private final UserJdbcRepository userJdbcRepository;

    @Autowired
    public SmsNewsletterServiceImpl(SmsService smsService, UserJdbcRepository userJdbcRepository) {
        this.smsService = smsService;
        this.userJdbcRepository = userJdbcRepository;
    }

    @Override
    public void sendSmsToAllUsers(String message) throws SmsServiceException, InvalidPhoneNumberException {
        List<String> allPhoneNumbers = userJdbcRepository.findAllPhoneNumbers(); // fetches all phone numbers
        for (String phoneNumber : allPhoneNumbers) {
            smsService.sendSms(phoneNumber, message); // send SMS to each user
        }
    }

    @Override
    public void sendSmsToUsersByRole(UserRole userRole, String message) throws SmsServiceException, InvalidPhoneNumberException {
        List<String> phoneNumbers = userJdbcRepository.findPhoneNumbersByRole(userRole); // fetch phone numbers based on role
        for (String phoneNumber : phoneNumbers) {
            smsService.sendSms(phoneNumber, message); // send SMS to each user with the specific role
        }
    }

    @Override
    public void sendSmsToUser(String phoneNumber, String message) throws SmsServiceException, InvalidPhoneNumberException {
        // Validate the phone number before sending SMS
        List<String> errors = new ArrayList<>();
        if (!isValidPhoneNumber(phoneNumber)) {
            errors.add("Invalid phone number");
            throw new InvalidEntityException("Invalid phone number format.", ErrorCodes.INVALID_PHONE_NUMBER,errors);
        }
        smsService.sendSms(phoneNumber, message); // send SMS to a specific user
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        List<String> errors = new ArrayList<>();
        try {
            // Assuming the number to be valid in the US by default
            // Adjust the region code ("US") to the appropriate country code
            Phonenumber.PhoneNumber number = phoneNumberUtil.parse(phoneNumber, "US");
            return phoneNumberUtil.isValidNumber(number);
        } catch (Exception e) {
            errors.add("Invalid phone number");
            // Invalid number format if parsing or validation fails
            throw new InvalidEntityException("Invalid phone number format.", ErrorCodes.INVALID_PHONE_NUMBER,errors);
        }

    }


}

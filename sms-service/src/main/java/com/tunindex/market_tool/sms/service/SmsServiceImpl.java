package com.tunindex.market_tool.sms.service;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.exception.InvalidPhoneNumberException;
import com.tunindex.market_tool.common.exception.SmsServiceException;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class SmsServiceImpl implements SmsService {

    @Value("${twilio.account_sid}")  // Must match property file
    private String accountSid;

    @Value("${twilio.auth_token}")   // Must match property file
    private String authToken;

    @Value("${twilio.trial_number}")
    private String twilioPhoneNumber;

    // Initialize Twilio SDK with credentials after Spring dependency injection
    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
    }

    @Override
    public void sendSms(String to, String message) throws SmsServiceException, InvalidPhoneNumberException {
        if (to == null || to.trim().isEmpty()) {
            throw new InvalidPhoneNumberException("Phone number is required.");
        }

        try {
            // Validate the recipient phone number format
            if (!isValidPhoneNumber(to)) {
                throw new InvalidPhoneNumberException("Invalid recipient phone number format.");
            }

            // Send SMS using Twilio API
            Message.creator(
                            new com.twilio.type.PhoneNumber(to),
                            new com.twilio.type.PhoneNumber(twilioPhoneNumber),
                            message)
                    .create();

        } catch (ApiException e) {
            // Handle Twilio-specific API exceptions
            throw new SmsServiceException("Error occurred while sending SMS: " + e.getMessage(), e);
        } catch (Exception e) {
            // Catch any other exceptions and throw a custom SMS service exception
            throw new SmsServiceException("Error occurred while sending SMS: " + e.getMessage(), e);
        }
    }

    // Helper method to validate recipient phone number using libphonenumber
    private boolean isValidPhoneNumber(String phoneNumber) {
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        List<String> errors = new ArrayList<>();
        try {
            // Assuming the number to be valid in the US by default
            // Adjust the region code ("US") to the appropriate country code
            Phonenumber.PhoneNumber number = phoneNumberUtil.parse(phoneNumber, "US");
            return phoneNumberUtil.isValidNumber(number);
        } catch (Exception e) {
            errors.add(e.getMessage());
            // Invalid number format if parsing or validation fails
            throw new InvalidEntityException("Invalid phone number format.", ErrorCodes.INVALID_PHONE_NUMBER,errors);
        }

    }

}

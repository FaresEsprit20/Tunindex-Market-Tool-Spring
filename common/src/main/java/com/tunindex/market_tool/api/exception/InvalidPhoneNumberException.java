package com.tunindex.market_tool.api.exception;

public class InvalidPhoneNumberException extends SmsServiceException {

    public InvalidPhoneNumberException(String phoneNumber) {
        super("The phone number " + phoneNumber + " is invalid.");
    }

}

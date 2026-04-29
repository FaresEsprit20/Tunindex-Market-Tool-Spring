package com.tunindex.market_tool.core.exception;

public class InvalidPhoneNumberException extends SmsServiceException {

    public InvalidPhoneNumberException(String phoneNumber) {
        super("The phone number " + phoneNumber + " is invalid.");
    }

}

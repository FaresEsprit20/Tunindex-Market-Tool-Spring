package com.tunindex.market_tool.sms.service;


import com.tunindex.market_tool.common.exception.InvalidPhoneNumberException;
import com.tunindex.market_tool.common.exception.SmsServiceException;

public interface SmsService {

    void sendSms(String to, String message) throws SmsServiceException, InvalidPhoneNumberException;

}

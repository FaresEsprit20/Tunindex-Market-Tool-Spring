package com.tunindex.market_tool.recaptcha.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SmsNewsletterController implements SmsNewsletterApi {

    private final SmsNewsletterService smsNewsletterService;

    @Override
    public void sendToAll(SendToAllSmsDto dto) {
        smsNewsletterService.sendSmsToAllUsers(dto.getMessage());
    }

    @Override
    public void sendToRole(SendToRoleSmsDto dto) {
        smsNewsletterService.sendSmsToUsersByRole(dto.getRole(), dto.getMessage());
    }

    @Override
    public void sendToUser(SendToUserSmsDto dto) {
        smsNewsletterService.sendSmsToUser(dto.getPhoneNumber(), dto.getPhoneNumber());
    }


}
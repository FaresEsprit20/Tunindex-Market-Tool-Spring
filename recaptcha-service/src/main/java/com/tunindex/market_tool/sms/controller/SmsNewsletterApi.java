package com.tunindex.market_tool.sms.controller;

import com.tunindex.market_tool.sms.dto.SendToAllSmsDto;
import com.tunindex.market_tool.sms.dto.SendToRoleSmsDto;
import com.tunindex.market_tool.sms.dto.SendToUserSmsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SMS Newsletter", description = "API for sending SMS newsletters")
public interface SmsNewsletterApi {

    @PostMapping("/api/sms/newsletter/send-to-all")
    @Operation(summary = "Send SMS to all users",
            description = "Sends an SMS message to all users with valid phone numbers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SMS sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data or phone number format")
    })
    void sendToAll(
            @RequestBody SendToAllSmsDto dto
    );

    @PostMapping("/api/sms/newsletter/send-to-role")
    @Operation(summary = "Send SMS to users by role",
            description = "Sends an SMS message to users with a specific role")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SMS sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid role or phone number format")
    })
    void sendToRole(
            @RequestBody SendToRoleSmsDto dto
    );

    @PostMapping("/api/sms/newsletter/send-to-user")
    @Operation(summary = "Send SMS to a specific user",
            description = "Sends an SMS message to a specific phone number")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SMS sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid phone number or input data")
    })
    void sendToUser(
            @RequestBody SendToUserSmsDto dto
    );


}

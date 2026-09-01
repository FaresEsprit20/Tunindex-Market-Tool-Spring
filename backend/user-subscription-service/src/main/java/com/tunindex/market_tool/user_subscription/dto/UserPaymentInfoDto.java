package com.tunindex.market_tool.user_subscription.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserPaymentInfoDto {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String numTel;
}
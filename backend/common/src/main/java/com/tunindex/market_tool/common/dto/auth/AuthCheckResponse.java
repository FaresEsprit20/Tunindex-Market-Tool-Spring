package com.tunindex.market_tool.common.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class AuthCheckResponse {

    @JsonProperty("authenticated")  // Frontend expects "authenticated"
    private boolean isAuthenticated;
    private String email;
    private Integer userId;

}

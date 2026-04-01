package com.tunindex.market_tool.domain.dto.auth;

import lombok.*;

@Data
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthenticationResponse {

    private String accessToken;  // The access token

    private String refreshToken;


}

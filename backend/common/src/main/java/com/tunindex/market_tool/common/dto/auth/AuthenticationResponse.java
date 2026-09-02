package com.tunindex.market_tool.common.dto.auth;

import lombok.*;

@Data
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthenticationResponse {

    private String accessToken;  // The access token

    private String refreshToken;

    /**
     * True when the password check passed but a TOTP code is still needed —
     * accessToken/refreshToken are null in that case and mfaToken must be
     * submitted along with the code to /auth/two-factor/verify.
     */
    @Builder.Default
    private boolean requiresTwoFactor = false;

    private String mfaToken;

}

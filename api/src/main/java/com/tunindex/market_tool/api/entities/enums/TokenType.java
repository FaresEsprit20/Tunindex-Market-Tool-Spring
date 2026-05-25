package com.tunindex.market_tool.api.entities.enums;

public enum TokenType {
    @Deprecated JWT,              // To be removed after migration
    OAUTH2_ACCESS,                // OAuth2 access token (short-lived)
    OAUTH2_REFRESH,               // OAuth2 refresh token (long-lived)
    PASSWORD_RESET,               // Password reset tokens
    TWO_FACTOR                    // 2FA verification tokens
}
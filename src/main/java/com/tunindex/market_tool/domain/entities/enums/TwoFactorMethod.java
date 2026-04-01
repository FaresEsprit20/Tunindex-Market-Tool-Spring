package com.tunindex.market_tool.domain.entities.enums;

public enum TwoFactorMethod {

    AUTHENTICATOR,  // For TOTP apps like Google Authenticator
    SMS,            // For SMS delivery
    EMAIL           // For email delivery (the only one we'll use)

} 
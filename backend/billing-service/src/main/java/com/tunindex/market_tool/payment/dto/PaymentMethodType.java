package com.tunindex.market_tool.payment.dto;

import lombok.Getter;

@Getter
public enum PaymentMethodType {
    BANK_CARD("bank_card"),
    E_DINAR("e-DINAR"),
    FLOUCI("flouci"),
    WALLET("wallet");

    private final String konnectValue;

    PaymentMethodType(String konnectValue) {
        this.konnectValue = konnectValue;
    }

    public static PaymentMethodType fromKonnectValue(String value) {
        for (PaymentMethodType method : values()) {
            if (method.konnectValue.equalsIgnoreCase(value)) {
                return method;
            }
        }
        return BANK_CARD;
    }
}
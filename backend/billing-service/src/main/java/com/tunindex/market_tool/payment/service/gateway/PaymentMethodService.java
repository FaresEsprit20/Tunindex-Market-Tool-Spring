package com.tunindex.market_tool.payment.service.gateway;

import com.tunindex.market_tool.payment.config.KonnectConfig;
import com.tunindex.market_tool.payment.dto.PaymentMethodResponseDto;
import com.tunindex.market_tool.payment.dto.PaymentMethodType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final KonnectConfig konnectConfig;

    public List<PaymentMethodResponseDto> getAvailablePaymentMethods() {
        List<PaymentMethodType> allowedMethods = konnectConfig.getAllowedPaymentMethods();

        return allowedMethods.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    private PaymentMethodResponseDto toResponseDto(PaymentMethodType method) {
        switch (method) {
            case BANK_CARD:
                return PaymentMethodResponseDto.builder()
                        .code("BANK_CARD")
                        .name("Credit/Debit Card")
                        .description("Pay with CIB, Visa, or Mastercard")
                        .iconUrl("/icons/card.svg")
                        .isActive(true)
                        .displayOrder(1)
                        .build();
            case E_DINAR:
                return PaymentMethodResponseDto.builder()
                        .code("E_DINAR")
                        .name("e-DINAR")
                        .description("Pay with Tunisian Post e-DINAR")
                        .iconUrl("/icons/edinar.svg")
                        .isActive(true)
                        .displayOrder(2)
                        .build();
            case FLOUCI:
                return PaymentMethodResponseDto.builder()
                        .code("FLOUCI")
                        .name("Flouci")
                        .description("Pay with Flouci app")
                        .iconUrl("/icons/flouci.svg")
                        .isActive(true)
                        .displayOrder(3)
                        .build();
            case WALLET:
                return PaymentMethodResponseDto.builder()
                        .code("WALLET")
                        .name("Konnect Wallet")
                        .description("Pay using your Konnect wallet balance")
                        .iconUrl("/icons/wallet.svg")
                        .isActive(true)
                        .displayOrder(4)
                        .build();
            default:
                return null;
        }
    }


}
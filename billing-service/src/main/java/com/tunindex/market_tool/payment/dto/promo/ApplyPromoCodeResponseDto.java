package com.tunindex.market_tool.payment.dto.promo;

import com.tunindex.market_tool.payment.entities.enums.DiscountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ApplyPromoCodeResponseDto {
    private boolean valid;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal originalAmount;
    private BigDecimal discountedAmount;
    private BigDecimal savings;
    private String message;
}

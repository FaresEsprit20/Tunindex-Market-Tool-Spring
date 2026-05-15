package com.tunindex.market_tool.api.services.promo;



public interface PromoCodeService {

    ApplyPromoCodeResponseDto applyPromoCode(ApplyPromoCodeRequestDto request);

    void usePromoCode(String code, Long transactionId);

    PromoCodeDto createPromoCode(PromoCodeDto dto);

    PromoCodeDto updatePromoCode(Long id, PromoCodeDto dto);

    PromoCodeDto findById(Long id);

    PromoCodeDto findByCode(String code);

    void deletePromoCode(Long id);

    void deactivatePromoCode(Long id);
}
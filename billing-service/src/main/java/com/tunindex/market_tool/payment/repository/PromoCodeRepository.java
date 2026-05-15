package com.tunindex.market_tool.payment.repository;

import com.tunindex.market_tool.payment.entities.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, Long>, JpaSpecificationExecutor<PromoCode> {

    Optional<PromoCode> findByCode(String code);

    @Query("SELECT p FROM PromoCode p WHERE p.code = :code AND p.isActive = true " +
            "AND (p.validFrom IS NULL OR p.validFrom <= :now) " +
            "AND (p.validUntil IS NULL OR p.validUntil >= :now) " +
            "AND (p.usageLimit IS NULL OR p.usedCount < p.usageLimit)")
    Optional<PromoCode> findValidPromoCode(@Param("code") String code, @Param("now") LocalDateTime now);

    boolean existsByCode(String code);

}
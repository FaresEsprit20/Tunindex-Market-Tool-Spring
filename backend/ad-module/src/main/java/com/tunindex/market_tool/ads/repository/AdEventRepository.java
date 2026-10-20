package com.tunindex.market_tool.ads.repository;

import com.tunindex.market_tool.ads.entities.AdEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface AdEventRepository extends JpaRepository<AdEvent, Long> {

    List<AdEvent> findByAdvertisementIdOrderByOccurredAtDesc(Long advertisementId);

    long countByAdvertisementIdAndEventType(Long advertisementId, AdEvent.EventType eventType);

    /**
     * Total earned by one ad.
     *
     * <p>Coalesced to zero so an ad with no events yet reports 0 rather than
     * null - callers add these up, and a null in a sum is a crash waiting for
     * the first unserved campaign.
     */
    @Query("select coalesce(sum(e.revenue), 0) from AdEvent e where e.advertisementId = :adId")
    BigDecimal totalRevenueFor(@Param("adId") Long adId);

    @Query("""
            select coalesce(sum(e.revenue), 0) from AdEvent e
            where e.occurredAt >= :from and e.occurredAt < :to
            """)
    BigDecimal totalRevenueBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select e.eventType, count(e) from AdEvent e
            where e.advertisementId = :adId
            group by e.eventType
            """)
    List<Object[]> countsByTypeFor(@Param("adId") Long adId);
}

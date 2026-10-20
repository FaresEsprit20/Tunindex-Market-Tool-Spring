package com.tunindex.market_tool.ads.repository;

import com.tunindex.market_tool.ads.entities.Advertisement;
import com.tunindex.market_tool.ads.entities.enums.AdPlacement;
import com.tunindex.market_tool.ads.entities.enums.AdStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {

    List<Advertisement> findByStatusOrderByPriorityDesc(AdStatus status);

    List<Advertisement> findByPlacementOrderByPriorityDesc(AdPlacement placement);

    /**
     * Ads eligible for a slot right now.
     *
     * <p>The date window is applied in the query rather than filtered
     * afterwards so an expired campaign never reaches the selection step. The
     * budget check stays in Java: it compares two nullable money columns, and
     * expressing that in JPQL is harder to read than it is worth.
     */
    @Query("""
            select a from Advertisement a
            where a.placement = :placement
              and a.status = :status
              and (a.startsAt is null or a.startsAt <= :now)
              and (a.endsAt is null or a.endsAt >= :now)
            order by a.priority desc
            """)
    List<Advertisement> findCandidates(@Param("placement") AdPlacement placement,
                                       @Param("status") AdStatus status,
                                       @Param("now") LocalDateTime now);
}

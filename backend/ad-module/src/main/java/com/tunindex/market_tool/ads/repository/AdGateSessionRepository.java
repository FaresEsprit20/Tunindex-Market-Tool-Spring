package com.tunindex.market_tool.ads.repository;

import com.tunindex.market_tool.ads.entities.AdGateSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AdGateSessionRepository extends JpaRepository<AdGateSession, Long> {

    Optional<AdGateSession> findByToken(String token);

    /**
     * Clears out sessions nobody came back to finish.
     *
     * <p>Abandoned rows are the normal case, not an error - people close tabs
     * part-way through ads. They are worth deleting only so the table does not
     * grow without bound.
     */
    @Modifying
    @Query("delete from AdGateSession s where s.startedAt < :cutoff")
    int deleteStartedBefore(@Param("cutoff") LocalDateTime cutoff);
}

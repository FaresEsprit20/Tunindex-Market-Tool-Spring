package com.tunindex.market_tool.api.repository;

import com.tunindex.market_tool.api.entities.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByUserIdOrderByAddedAtAsc(Integer userId);

    boolean existsByUserIdAndSymbol(Integer userId, String symbol);

    @Modifying
    @Query("delete from WatchlistItem w where w.user.id = :userId and w.symbol = :symbol")
    void deleteByUserIdAndSymbol(@Param("userId") Integer userId, @Param("symbol") String symbol);
}

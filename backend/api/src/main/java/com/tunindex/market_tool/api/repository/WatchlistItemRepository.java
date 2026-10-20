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

    /**
     * Every watched item with its owner already loaded.
     *
     * <p>The {@code user} relation is LAZY, so a plain {@code findAll()}
     * hands back detached rows whose {@code getUser()} throws the moment the
     * transaction ends — and the watchlist monitor runs outside one on
     * purpose, because it makes a blocking HTTP call. The fetch join also
     * turns what would be an N+1 into a single query.
     */
    @Query("select w from WatchlistItem w join fetch w.user order by w.symbol asc")
    List<WatchlistItem> findAllWithUser();

    boolean existsByUserIdAndSymbol(Integer userId, String symbol);

    @Modifying
    @Query("delete from WatchlistItem w where w.user.id = :userId and w.symbol = :symbol")
    void deleteByUserIdAndSymbol(@Param("userId") Integer userId, @Param("symbol") String symbol);
}

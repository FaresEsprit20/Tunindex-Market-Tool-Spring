package com.tunindex.market_tool.collector.repository.jpa;

import com.tunindex.market_tool.collector.entities.ScoreSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScoreSnapshotRepository extends JpaRepository<ScoreSnapshot, Long> {

    Optional<ScoreSnapshot> findBySymbolAndSnapshotDate(String symbol, LocalDate snapshotDate);

    /** One symbol's score history, oldest first — drives the score sparkline. */
    List<ScoreSnapshot> findBySymbolAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            String symbol, LocalDate from);

    long countBySnapshotDate(LocalDate snapshotDate);
}

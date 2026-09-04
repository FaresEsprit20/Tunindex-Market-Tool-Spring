package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.dto.market.MarketBreadthDto;
import com.tunindex.market_tool.collector.dto.market.SectorPerformanceDto;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.collector.services.market.MarketBreadthService;
import com.tunindex.market_tool.common.entities.embedded.PriceData;
import com.tunindex.market_tool.common.entities.embedded.VolumeData;
import com.tunindex.market_tool.common.entities.enums.SectorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketBreadthService")
class MarketBreadthServiceTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private MarketBreadthService service;

    private Stock stock(String symbol, SectorType sector, String last, String prev, Long volume) {
        return Stock.builder()
                .symbol(symbol)
                .name(symbol + " SA")
                .sector(sector)
                .lastUpdate(LocalDateTime.of(2026, 9, 3, 15, 30))
                .priceData(PriceData.builder()
                        .lastPrice(last == null ? null : new BigDecimal(last))
                        .prevClose(prev == null ? null : new BigDecimal(prev))
                        .build())
                .volumeData(VolumeData.builder().volume(volume).build())
                .build();
    }

    @Test
    @DisplayName("classifies every stock and the counts reconcile to the total")
    void countsReconcile() {
        when(stockRepository.findAll()).thenReturn(List.of(
                stock("UP1", SectorType.BANKING, "110", "100", 10L),
                stock("UP2", SectorType.BANKING, "105", "100", 20L),
                stock("DOWN", SectorType.INSURANCE, "90", "100", 30L),
                stock("FLAT", SectorType.INSURANCE, "100", "100", 40L),
                stock("NOPRICE", SectorType.MATERIALS, null, "100", 50L)));

        MarketBreadthDto breadth = service.breadth();

        assertThat(breadth.getAdvancing()).isEqualTo(2);
        assertThat(breadth.getDeclining()).isEqualTo(1);
        assertThat(breadth.getUnchanged()).isEqualTo(1);
        assertThat(breadth.getNotPriced()).isEqualTo(1);
        assertThat(breadth.getTotal()).isEqualTo(5);
        // The invariant the UI depends on to render a bar that always fills.
        assertThat(breadth.getAdvancing() + breadth.getDeclining()
                + breadth.getUnchanged() + breadth.getNotPriced())
                .isEqualTo(breadth.getTotal());
    }

    @Test
    @DisplayName("a stock with no price is excluded from the average, not counted as flat")
    void unpricedDoesNotDragTheAverage() {
        when(stockRepository.findAll()).thenReturn(List.of(
                stock("UP", SectorType.BANKING, "110", "100", 1L),
                stock("NOPRICE", SectorType.BANKING, null, "100", 1L)));

        MarketBreadthDto breadth = service.breadth();

        // Averaging over 2 names would give 5.00; only the priced one counts.
        assertThat(breadth.getAverageChangePct()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("a zero previous close is treated as unpriced rather than dividing by zero")
    void zeroPrevCloseIsUnpriced() {
        when(stockRepository.findAll()).thenReturn(List.of(
                stock("ZERO", SectorType.BANKING, "10", "0", 1L)));

        MarketBreadthDto breadth = service.breadth();

        assertThat(breadth.getNotPriced()).isEqualTo(1);
        assertThat(breadth.getAverageChangePct()).isNull();
    }

    @Test
    @DisplayName("movers lists exclude names that did not move the right way")
    void moversAreDirectional() {
        when(stockRepository.findAll()).thenReturn(List.of(
                stock("UP", SectorType.BANKING, "110", "100", 1L),
                stock("FLAT", SectorType.BANKING, "100", "100", 1L),
                stock("DOWN", SectorType.BANKING, "90", "100", 1L)));

        MarketBreadthDto breadth = service.breadth();

        assertThat(breadth.getTopGainers()).extracting("symbol").containsExactly("UP");
        assertThat(breadth.getTopLosers()).extracting("symbol").containsExactly("DOWN");
    }

    @Test
    @DisplayName("sectors are ordered best to worst and averaged equal-weighted")
    void sectorsRankedAndAveraged() {
        when(stockRepository.findAll()).thenReturn(List.of(
                stock("B1", SectorType.BANKING, "110", "100", 1L),
                stock("B2", SectorType.BANKING, "120", "100", 1L),
                stock("I1", SectorType.INSURANCE, "95", "100", 1L)));

        List<SectorPerformanceDto> sectors = service.breadth().getSectorPerformance();

        assertThat(sectors).extracting("sector").containsExactly("BANKING", "INSURANCE");
        // (10 + 20) / 2 — equal-weighted, not value-weighted.
        assertThat(sectors.get(0).getAverageChangePct()).isEqualByComparingTo("15.00");
        assertThat(sectors.get(0).getPriced()).isEqualTo(2);
        assertThat(sectors.get(1).getAverageChangePct()).isEqualByComparingTo("-5.00");
    }

    @Test
    @DisplayName("a sector with nothing priced still appears, with a null average")
    void unpricedSectorStillReported() {
        when(stockRepository.findAll()).thenReturn(List.of(
                stock("B1", SectorType.BANKING, "110", "100", 1L),
                stock("M1", SectorType.MATERIALS, null, null, 1L)));

        List<SectorPerformanceDto> sectors = service.breadth().getSectorPerformance();

        assertThat(sectors).extracting("sector").contains("MATERIALS");
        SectorPerformanceDto materials = sectors.stream()
                .filter(sector -> sector.getSector().equals("MATERIALS"))
                .findFirst()
                .orElseThrow();
        assertThat(materials.getAverageChangePct()).isNull();
        assertThat(materials.getTotal()).isEqualTo(1);
        assertThat(materials.getPriced()).isZero();
        // Nulls sort last so a "no data" row never outranks a real gainer.
        assertThat(sectors.get(sectors.size() - 1).getSector()).isEqualTo("MATERIALS");
    }

    @Test
    @DisplayName("an empty market returns zeroed counts rather than throwing")
    void emptyMarket() {
        when(stockRepository.findAll()).thenReturn(List.of());

        MarketBreadthDto breadth = service.breadth();

        assertThat(breadth.getTotal()).isZero();
        assertThat(breadth.getAverageChangePct()).isNull();
        assertThat(breadth.getTopGainers()).isEmpty();
        assertThat(breadth.getSectorPerformance()).isEmpty();
    }
}

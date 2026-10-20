package com.tunindex.market_tool.collector.providers.ilboursa;

import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.common.entities.embedded.CalculatedValues;
import com.tunindex.market_tool.common.entities.embedded.FundamentalData;
import com.tunindex.market_tool.common.entities.embedded.PriceData;
import com.tunindex.market_tool.common.entities.embedded.RatiosData;
import com.tunindex.market_tool.common.entities.embedded.TechnicalData;
import com.tunindex.market_tool.common.entities.embedded.VolumeData;
import com.tunindex.market_tool.common.entities.enums.SectorType;
import com.tunindex.market_tool.common.utils.constants.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds stocks for the listed companies the primary source does not carry.
 *
 * <p>Twelve companies on this exchange have no page on stockanalysis.com at
 * all - checked one by one, every request returns 404. They were absent from
 * the app not because a scrape failed but because nothing had ever looked for
 * them: comparing the configured universe against the exchange's own cote is
 * what surfaced them.
 *
 * <p>Everything here comes from sources that <em>do</em> list them:
 *
 * <ul>
 *   <li>the cote table - price, open, high, low, volume, for all ninety
 *       listings in a single request
 *   <li>the company page - revenue, net income, earnings per share, P/E
 *   <li>the quote page - 52-week range, one-year change, dividend
 * </ul>
 *
 * <p>These stocks are deliberately thinner than the primary ones: there is no
 * book value, no debt-to-equity, no analyst view, because no source publishes
 * those for them. A stock with a real price and honest gaps is worth more than
 * a company the app pretends does not exist - and the scorer already drops
 * components it has no data for rather than guessing at them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IlBoursaStockProvider {

    private static final String EXCHANGE = "Tunis Stock Exchange";
    private static final String EXCHANGE_FULL_NAME = "BVMT";
    private static final String CURRENCY = "TND";
    private static final String MARKET = "Tunisia";

    private final IlBoursaMarketTableProvider marketTable;
    private final IlBoursaFundamentalsProvider fundamentalsProvider;
    private final IlBoursaCotationProvider cotationProvider;

    /**
     * Every secondary-universe company the sources could describe.
     *
     * <p>The cote is fetched once for the whole set rather than per symbol -
     * it carries all of them in one response, and twelve separate requests
     * for data already in hand would be rude for no gain.
     */
    public List<Stock> fetchAll() {
        Map<String, IlBoursaMarketTableProvider.MarketRow> cote = marketTable.fetchAll();
        if (cote.isEmpty()) {
            log.warn("Secondary universe: the cote table was unavailable, skipping this pass");
            return List.of();
        }

        List<Stock> stocks = new ArrayList<>();
        for (Map.Entry<String, Constants.StockInfo> entry
                : Constants.TUNISIAN_STOCKS_SECONDARY.entrySet()) {

            Stock stock = build(entry.getKey(), entry.getValue(), cote.get(entry.getKey()));
            if (stock != null) {
                stocks.add(stock);
            }
        }

        log.info("Secondary universe: built {} of {} companies",
                stocks.size(), Constants.TUNISIAN_STOCKS_SECONDARY.size());
        return stocks;
    }

    private Stock build(String symbol, Constants.StockInfo info,
                        IlBoursaMarketTableProvider.MarketRow row) {

        if (row == null || row.last() == null || row.last().signum() <= 0) {
            // No price means nothing worth storing: every downstream figure
            // is derived from one, and a company with no quote is not
            // something the app can say anything useful about.
            log.debug("Secondary universe: no quote for {}, skipping", symbol);
            return null;
        }

        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setName(info.name());
        stock.setUrl(info.url());
        stock.setExchange(EXCHANGE);
        stock.setExchangeFullName(EXCHANGE_FULL_NAME);
        stock.setMarket(MARKET);
        stock.setCurrency(CURRENCY);
        stock.setOwnershipType(info.ownershipType());
        stock.setIndustry(info.industry());
        stock.setSector(sectorFor(info.industry()));

        PriceData price = new PriceData();
        price.setLastPrice(row.last());
        price.setDayHigh(row.high());
        price.setDayLow(row.low());
        stock.setPriceData(price);

        VolumeData volume = new VolumeData();
        volume.setVolume(row.volumeShares());
        stock.setVolumeData(volume);

        FundamentalData fundamentals = new FundamentalData();
        RatiosData ratios = new RatiosData();
        stock.setFundamentalData(fundamentals);
        stock.setRatiosData(ratios);
        stock.setTechnicalData(new TechnicalData());
        stock.setCalculatedValues(new CalculatedValues());

        applyFundamentals(symbol, fundamentals, ratios);
        applyCotation(symbol, stock, price, fundamentals);

        return stock;
    }

    private void applyFundamentals(String symbol, FundamentalData fundamentals, RatiosData ratios) {
        IlBoursaFundamentalsProvider.Fundamentals source = fundamentalsProvider.fetch(symbol);
        if (source == null) {
            // Refused as stale, or the company has stopped filing. Left empty
            // rather than filled with figures from years ago.
            return;
        }
        fundamentals.setEps(source.eps());
        fundamentals.setPeRatio(source.peRatio());
        fundamentals.setRevenue(source.revenue());
        fundamentals.setSharesOutstanding(source.sharesOutstanding());
        ratios.setProfitMargin(source.profitMargin());
    }

    private void applyCotation(String symbol, Stock stock, PriceData price, FundamentalData fundamentals) {
        IlBoursaCotationProvider.Cotation source = cotationProvider.fetch(symbol);
        if (source == null) {
            return;
        }

        price.setWeek52High(source.week52High());
        price.setWeek52Low(source.week52Low());
        if (source.week52High() != null && source.week52Low() != null) {
            price.setWeek52Range(source.week52Low() + " - " + source.week52High());
        }
        fundamentals.setOneYearReturn(source.oneYearReturnPct());

        // The dividend is published as an amount; the yield is computed here
        // against our own price rather than copied from the page's own
        // "Rendement" column, which is measured against the price at
        // distribution and runs well above the current yield.
        if (source.dividendPerShare() != null
                && source.dividendPerShare().signum() > 0
                && price.getLastPrice().signum() > 0) {

            fundamentals.setDividendYield(source.dividendPerShare()
                    .divide(price.getLastPrice(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * Maps the configured industry text onto the sector enum.
     *
     * <p>Falls back to OTHER rather than guessing. The value is written to a
     * column with a CHECK constraint, so an invented sector would not be a
     * cosmetic error - it would reject the whole row.
     */
    private SectorType sectorFor(String industry) {
        if (industry == null) {
            return SectorType.OTHER;
        }
        String text = industry.toLowerCase();
        if (text.contains("bank")) return SectorType.BANKING;
        if (text.contains("insurance")) return SectorType.INSURANCE;
        if (text.contains("pharmaceutical") || text.contains("health")) return SectorType.HEALTHCARE;
        if (text.contains("computer") || text.contains("software")) return SectorType.TECHNOLOGY;
        if (text.contains("chemical") || text.contains("nonferrous")) return SectorType.MATERIALS;
        if (text.contains("groceries") || text.contains("dairy") || text.contains("beverage")) {
            return SectorType.CONSUMER_GOODS;
        }
        if (text.contains("transportation") || text.contains("manufacturing")) return SectorType.INDUSTRIALS;
        return SectorType.OTHER;
    }
}

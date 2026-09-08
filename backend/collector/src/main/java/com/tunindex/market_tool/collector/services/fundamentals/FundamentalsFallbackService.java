package com.tunindex.market_tool.collector.services.fundamentals;

import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.providers.bvmt.BvmtBulletinProvider;
import com.tunindex.market_tool.collector.providers.ilboursa.IlBoursaCotationProvider;
import com.tunindex.market_tool.collector.providers.ilboursa.IlBoursaFundamentalsProvider;
import com.tunindex.market_tool.collector.providers.ilboursa.IlBoursaMarketTableProvider;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.common.entities.embedded.CalculatedValues;
import com.tunindex.market_tool.common.entities.embedded.FundamentalData;
import com.tunindex.market_tool.common.entities.embedded.PriceData;
import com.tunindex.market_tool.common.entities.embedded.RatiosData;
import com.tunindex.market_tool.common.entities.embedded.VolumeData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills fundamentals the primary source did not supply, from a second source.
 *
 * <p>The primary (stockanalysis.com) is good but not complete for this
 * market: it reports {@code n/a} for individual figures, and has no page at
 * all for a number of listed companies. This service asks ilboursa for the
 * gaps that remain after parsing and arithmetic derivation have both had a
 * turn.
 *
 * <p>Three rules keep a second source from making the data worse:
 *
 * <ol>
 *   <li><b>Only blanks are filled.</b> A field the primary supplied is never
 *       overwritten. The two sources compute over different periods -
 *       stockanalysis on a trailing twelve months, ilboursa on the last
 *       published fiscal year - so their figures legitimately differ, and
 *       swapping one for the other would change a stock's numbers with no
 *       visible cause.
 *   <li><b>Stale years are refused</b> by the provider itself, so a company
 *       that stopped filing contributes nothing rather than a figure four
 *       years out of date.
 *   <li><b>Every fill is logged with its source and fiscal year</b>, so any
 *       number the scorer used can be traced back to where it came from.
 * </ol>
 *
 * <p>Derivation runs again afterwards, because a newly supplied EPS can
 * unlock a P/E, and that P/E can unlock a payout ratio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FundamentalsFallbackService {

    private final StockRepository stockRepository;
    private final BvmtBulletinProvider bvmtBulletin;
    private final IlBoursaFundamentalsProvider ilBoursaFundamentals;
    private final IlBoursaCotationProvider ilBoursaCotation;
    private final IlBoursaMarketTableProvider ilBoursaMarketTable;
    private final FundamentalsDeriver fundamentalsDeriver;

    /**
     * How recent a dividend must be to count as a live one.
     *
     * <p>The bulletin prints the last dividend a company paid however long ago
     * that was - ATB's is dated 2019 and SIPHAT's 2011. Dividing a fifteen-
     * year-old coupon by today's price would manufacture a yield for a company
     * that has not paid in a decade, which is exactly the kind of confident
     * wrong number the scorer must never see.
     */
    private static final int MAX_DIVIDEND_AGE_YEARS = 2;

    /** Pause between symbols, so a sweep stays polite to the source site. */
    private static final long DELAY_BETWEEN_SYMBOLS_MS = 1500;

    /**
     * Fills gaps across every stock that still has one.
     *
     * @return a per-field count of what the fallback supplied
     */
    @Transactional
    public Map<String, Integer> fillGaps() {
        Map<String, Integer> filled = new LinkedHashMap<>();
        List<Stock> stocks = stockRepository.findAll();
        int consulted = 0;
        int helped = 0;

        // Both whole-market sources are fetched once up front rather than per
        // symbol. The bulletin is the exchange's own record, so it is consulted
        // first and the third-party table only fills what it leaves.
        BvmtBulletinProvider.Bulletin bulletin = bvmtBulletin.fetchLatest();
        Map<String, IlBoursaMarketTableProvider.MarketRow> cote = ilBoursaMarketTable.fetchAll();

        if (bulletin != null) {
            log.info("Using BVMT bulletin of {} ({} securities) as the primary fallback",
                    bulletin.sessionDate(), bulletin.quotes().size());
        }

        for (Stock stock : stocks) {
            if (!hasGap(stock)) {
                continue;
            }

            consulted++;
            List<String> applied = new ArrayList<>();

            // Official record first, then the third-party table for whatever
            // it did not carry. Both are already in memory.
            if (bulletin != null) {
                applied.addAll(applyBulletin(stock, bulletin.quotes().get(stock.getSymbol())));
            }
            applied.addAll(applyCote(stock, cote.get(stock.getSymbol())));

            IlBoursaFundamentalsProvider.Fundamentals fundamentals =
                    ilBoursaFundamentals.fetch(stock.getSymbol());
            Integer fiscalYear = null;
            if (fundamentals != null) {
                fiscalYear = fundamentals.fiscalYear();
                applied.addAll(apply(stock, fundamentals));
            }
            pause();

            // Only worth a second request if something it carries is still
            // missing after the company page had its turn.
            if (needsCotation(stock)) {
                applied.addAll(applyCotation(stock, ilBoursaCotation.fetch(stock.getSymbol())));
                pause();
            }

            // A supplied EPS makes a P/E derivable, and that P/E makes the
            // payout ratio derivable in turn.
            applied.addAll(fundamentalsDeriver.derive(stock));

            if (!applied.isEmpty()) {
                stockRepository.save(stock);
                helped++;
                applied.forEach(field -> filled.merge(field, 1, Integer::sum));
                log.info("Fallback filled {} for {} from ilboursa{}",
                        applied, stock.getSymbol(),
                        fiscalYear == null ? "" : " (FY" + fiscalYear + ")");
            }
        }

        log.info("Fundamentals fallback: consulted {} stocks with gaps, filled {} - {}",
                consulted, helped, filled);
        return filled;
    }

    /** True if anything the fallback could supply is still missing. */
    private boolean hasGap(Stock stock) {
        FundamentalData fundamentals = stock.getFundamentalData();
        RatiosData ratios = stock.getRatiosData();

        return fundamentals == null
                || ratios == null
                || fundamentals.getEps() == null
                || fundamentals.getPeRatio() == null
                || fundamentals.getRevenue() == null
                || fundamentals.getSharesOutstanding() == null
                || fundamentals.getDividendYield() == null
                || fundamentals.getPayoutRatio() == null
                || ratios.getProfitMargin() == null;
    }

    private List<String> apply(Stock stock, IlBoursaFundamentalsProvider.Fundamentals source) {
        List<String> applied = new ArrayList<>();

        if (stock.getFundamentalData() == null) {
            stock.setFundamentalData(new FundamentalData());
        }
        if (stock.getRatiosData() == null) {
            stock.setRatiosData(new RatiosData());
        }
        if (stock.getCalculatedValues() == null) {
            stock.setCalculatedValues(new CalculatedValues());
        }

        FundamentalData fundamentals = stock.getFundamentalData();
        RatiosData ratios = stock.getRatiosData();

        if (fundamentals.getEps() == null && source.eps() != null) {
            fundamentals.setEps(source.eps());
            applied.add("eps");
        }
        if (fundamentals.getPeRatio() == null && positive(source.peRatio())) {
            fundamentals.setPeRatio(source.peRatio());
            applied.add("peRatio");
        }
        if (fundamentals.getRevenue() == null && source.revenue() != null) {
            fundamentals.setRevenue(source.revenue());
            applied.add("revenue");
        }
        if (fundamentals.getSharesOutstanding() == null && source.sharesOutstanding() != null) {
            fundamentals.setSharesOutstanding(source.sharesOutstanding());
            applied.add("sharesOutstanding");
        }
        if (ratios.getProfitMargin() == null && source.profitMargin() != null) {
            ratios.setProfitMargin(source.profitMargin());
            applied.add("profitMargin");
        }

        // Dividend yield is the dividend per share against today's price. The
        // page publishes the dividend, not the yield, so this is the one
        // figure we compute rather than copy - and it is only meaningful
        // against a price, which is why it is guarded on one.
        if (fundamentals.getDividendYield() == null
                && source.dividendPerShare() != null
                && stock.getPriceData() != null
                && positive(stock.getPriceData().getLastPrice())) {

            BigDecimal yield = source.dividendPerShare()
                    .divide(stock.getPriceData().getLastPrice(), 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            fundamentals.setDividendYield(yield);
            applied.add("dividendYield");
        }

        return applied;
    }

    /**
     * Price and dividend from the exchange's own bulletin.
     *
     * <p>Preferred over every other fallback because it is the venue's record
     * of the session rather than a third party's copy of it, and because it is
     * the only source that dates its dividend - which is what makes the
     * dividend usable at all.
     */
    private List<String> applyBulletin(Stock stock, BvmtBulletinProvider.Quote quote) {
        List<String> applied = new ArrayList<>();
        if (quote == null) {
            return applied;
        }

        if (stock.getPriceData() == null) {
            stock.setPriceData(new PriceData());
        }
        if (stock.getFundamentalData() == null) {
            stock.setFundamentalData(new FundamentalData());
        }
        PriceData price = stock.getPriceData();
        FundamentalData fundamentals = stock.getFundamentalData();

        if (price.getLastPrice() == null && positive(quote.last())) {
            price.setLastPrice(quote.last());
            applied.add("lastPrice");
        }
        if (price.getDayHigh() == null && positive(quote.high())) {
            price.setDayHigh(quote.high());
            applied.add("dayHigh");
        }
        if (price.getDayLow() == null && positive(quote.low())) {
            price.setDayLow(quote.low());
            applied.add("dayLow");
        }
        if (price.getPrevClose() == null && positive(quote.close())) {
            price.setPrevClose(quote.close());
            applied.add("prevClose");
        }

        if (fundamentals.getDividendYield() == null
                && positive(quote.lastDividend())
                && isRecent(quote.lastDividendDate())
                && positive(price.getLastPrice())) {

            fundamentals.setDividendYield(quote.lastDividend()
                    .divide(price.getLastPrice(), 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP));
            applied.add("dividendYield");
        }

        return applied;
    }

    /** A dividend older than this describes a company that has stopped paying. */
    private boolean isRecent(LocalDate dividendDate) {
        return dividendDate != null
                && dividendDate.isAfter(LocalDate.now().minusYears(MAX_DIVIDEND_AGE_YEARS));
    }

    /** True if the quote page carries something we still lack. */
    private boolean needsCotation(Stock stock) {
        return stock.getPriceData() == null
                || stock.getPriceData().getWeek52High() == null
                || stock.getPriceData().getWeek52Low() == null
                || stock.getFundamentalData() == null
                || stock.getFundamentalData().getOneYearReturn() == null
                || stock.getFundamentalData().getDividendYield() == null;
    }

    /**
     * Price and volume from the whole-cote table.
     *
     * <p>The only source that covers every listed company, so for a stock the
     * primary has no page for, this is the difference between a tradable quote
     * and nothing at all.
     */
    private List<String> applyCote(Stock stock, IlBoursaMarketTableProvider.MarketRow row) {
        List<String> applied = new ArrayList<>();
        if (row == null) {
            return applied;
        }

        if (stock.getPriceData() == null) {
            stock.setPriceData(new PriceData());
        }
        PriceData price = stock.getPriceData();

        if (price.getLastPrice() == null && positive(row.last())) {
            price.setLastPrice(row.last());
            applied.add("lastPrice");
        }
        if (price.getDayHigh() == null && positive(row.high())) {
            price.setDayHigh(row.high());
            applied.add("dayHigh");
        }
        if (price.getDayLow() == null && positive(row.low())) {
            price.setDayLow(row.low());
            applied.add("dayLow");
        }
        if (stock.getVolumeData() == null) {
            stock.setVolumeData(new VolumeData());
        }
        if (stock.getVolumeData().getVolume() == null && row.volumeShares() != null) {
            stock.getVolumeData().setVolume(row.volumeShares());
            applied.add("volume");
        }
        return applied;
    }

    /**
     * 52-week range, one-year return and the dividend, from the quote page.
     *
     * <p>The dividend arrives as an amount per share; the yield is computed
     * here against our own price rather than copied from the page's own
     * "Rendement" column, which is measured against the price at distribution
     * and runs far above the current yield.
     */
    private List<String> applyCotation(Stock stock, IlBoursaCotationProvider.Cotation source) {
        List<String> applied = new ArrayList<>();
        if (source == null) {
            return applied;
        }

        if (stock.getPriceData() == null) {
            stock.setPriceData(new PriceData());
        }
        if (stock.getFundamentalData() == null) {
            stock.setFundamentalData(new FundamentalData());
        }
        PriceData price = stock.getPriceData();
        FundamentalData fundamentals = stock.getFundamentalData();

        if (price.getWeek52High() == null && positive(source.week52High())) {
            price.setWeek52High(source.week52High());
            applied.add("week52High");
        }
        if (price.getWeek52Low() == null && positive(source.week52Low())) {
            price.setWeek52Low(source.week52Low());
            applied.add("week52Low");
        }
        if (price.getWeek52Range() == null
                && price.getWeek52Low() != null && price.getWeek52High() != null) {
            price.setWeek52Range(price.getWeek52Low() + " - " + price.getWeek52High());
        }
        if (fundamentals.getOneYearReturn() == null && source.oneYearReturnPct() != null) {
            fundamentals.setOneYearReturn(source.oneYearReturnPct());
            applied.add("oneYearReturn");
        }
        if (fundamentals.getDividendYield() == null
                && positive(source.dividendPerShare())
                && positive(price.getLastPrice())) {

            fundamentals.setDividendYield(source.dividendPerShare()
                    .divide(price.getLastPrice(), 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP));
            applied.add("dividendYield");
        }
        return applied;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private void pause() {
        try {
            Thread.sleep(DELAY_BETWEEN_SYMBOLS_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

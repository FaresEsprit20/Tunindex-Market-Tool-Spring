package com.tunindex.market_tool.api.services;

import com.tunindex.market_tool.api.services.stock.StockService;
import com.tunindex.market_tool.common.dto.providers.investingcom.StockDto;
import com.tunindex.market_tool.common.entities.Stock;
import com.tunindex.market_tool.common.entities.enums.OwnershipType;
import com.tunindex.market_tool.common.entities.enums.SectorType;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.repository.jpa.StockRepository;
import com.tunindex.market_tool.common.specification.StockSpecification;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.PaginationUtil;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockServiceImpl implements StockService {

    private final StockRepository stockRepository;

    @Override
    @Transactional(readOnly = true)
    public StockDto findBySymbol(String symbol) {
        log.info("🔍 Finding stock by symbol: {}", symbol);

        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasLength(symbol)) {
            errors.add("Stock symbol is null or empty");
            throw new InvalidEntityException("Stock symbol is invalid", ErrorCodes.INVALID_STOCK_SYMBOL, errors);
        }

        return stockRepository.findBySymbol(symbol)
                .map(StockDto::fromEntity)
                .orElseThrow(() -> {
                    errors.add("No stock found with symbol: " + symbol);
                    return new EntityNotFoundException(
                            "Stock not found with symbol: " + symbol,
                            ErrorCodes.STOCK_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public StockDto findBySymbolAndExchange(String symbol, String exchange) {
        log.info("🔍 Finding stock by symbol: {} and exchange: {}", symbol, exchange);

        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasLength(symbol)) {
            errors.add("Stock symbol is null or empty");
            throw new InvalidEntityException("Stock symbol is invalid", ErrorCodes.INVALID_STOCK_SYMBOL, errors);
        }

        if (!StringUtils.hasLength(exchange)) {
            errors.add("Exchange is null or empty");
            throw new InvalidEntityException("Exchange is invalid", ErrorCodes.STOCK_EXCHANGE_INVALID, errors);
        }

        return stockRepository.findBySymbolAndExchange(symbol, exchange)
                .map(StockDto::fromEntity)
                .orElseThrow(() -> {
                    errors.add("No stock found with symbol: " + symbol + " and exchange: " + exchange);
                    return new EntityNotFoundException(
                            "Stock not found with symbol: " + symbol + " and exchange: " + exchange,
                            ErrorCodes.STOCK_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> filterStocks(PaginationAndFilteringDto paginationDto) {
        log.info("🔍 Filtering stocks with pagination: page={}, size={}, filters={}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = buildSpecificationFromFilters(paginationDto.getFilters());
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Object[]> countStocksBySector() {
        log.info("📊 Counting stocks by sector");
        return stockRepository.countStocksBySector();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Object[]> countStocksByOwnership() {
        log.info("📊 Counting stocks by ownership type");
        return stockRepository.countStocksByOwnership();
    }

    @Override
    @Transactional
    public void refreshStockData(String symbol) {
        log.info("🔄 Refreshing stock data for symbol: {}", symbol);

        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasLength(symbol)) {
            errors.add("Stock symbol is null or empty");
            throw new InvalidEntityException("Stock symbol is invalid", ErrorCodes.INVALID_STOCK_SYMBOL, errors);
        }

        boolean exists = stockRepository.existsBySymbol(symbol);
        if (!exists) {
            errors.add("Stock not found with symbol: " + symbol);
            throw new EntityNotFoundException("Stock not found", ErrorCodes.STOCK_NOT_FOUND, errors);
        }

        stockRepository.updateLastUpdateTime(symbol);
        log.info("✅ Stock refresh initiated for: {}", symbol);
    }

    // ========== PRIVATE HELPER METHODS ==========

    private void validatePaginationDto(PaginationAndFilteringDto paginationDto) {
        List<String> errors = new ArrayList<>();

        if (paginationDto.getPage() == null || paginationDto.getPage() < 1) {
            errors.add("Page number must be greater than 0");
        }

        if (paginationDto.getSize() == null || paginationDto.getSize() < 1) {
            errors.add("Page size must be greater than 0");
        }

        if (paginationDto.getSize() != null && paginationDto.getSize() > 100) {
            errors.add("Page size cannot exceed 100");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid pagination parameters", ErrorCodes.PAGE_NOT_VALID, errors);
        }
    }

    private PagedResponse<StockDto> buildPagedResponse(Page<Stock> stockPage) {
        List<StockDto> content = stockPage.getContent()
                .stream()
                .map(StockDto::fromEntity)
                .toList();

        return new PagedResponse<>(
                content,
                stockPage.getNumber() + 1,
                stockPage.getSize(),
                stockPage.getTotalElements(),
                stockPage.getTotalPages()
        );
    }

    /**
     * Safely parses a BigDecimal from the filters map.
     * Returns null (and logs a warning) if the value is missing or malformed.
     */
    private BigDecimal parseBigDecimal(Map<String, String> filters, String key) {
        String value = filters.get(key);
        if (!StringUtils.hasLength(value)) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid numeric value for filter '{}': {}", key, value);
            return null;
        }
    }

    /**
     * Returns true only when the filter key is present AND its value is exactly "true".
     * Returns false only when the value is exactly "false".
     * Returns null when the key is absent — meaning "do not apply this filter".
     */
    private Boolean parseBooleanFilter(Map<String, String> filters, String key) {
        if (!filters.containsKey(key)) return null;
        String value = filters.get(key);
        if ("true".equalsIgnoreCase(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;
        return null;
    }

    private Specification<Stock> buildSpecificationFromFilters(Map<String, String> filters) {
        Specification<Stock> spec = StockSpecification.empty();

        if (filters == null || filters.isEmpty()) {
            return spec;
        }

        // ── BASIC ──────────────────────────────────────────────────────────────

        if (StringUtils.hasLength(filters.get("symbol"))) {
            spec = spec.and(StockSpecification.symbolContains(filters.get("symbol")));
        }

        if (StringUtils.hasLength(filters.get("name"))) {
            spec = spec.and(StockSpecification.nameContains(filters.get("name")));
        }

        if (StringUtils.hasLength(filters.get("exchange"))) {
            spec = spec.and(StockSpecification.exchangeEquals(filters.get("exchange")));
        }

        if (StringUtils.hasLength(filters.get("sector"))) {
            try {
                SectorType sector = SectorType.valueOf(filters.get("sector").toUpperCase());
                spec = spec.and(StockSpecification.sectorEquals(sector));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid sector value: {}", filters.get("sector"));
            }
        }

        if (StringUtils.hasLength(filters.get("ownershipType"))) {
            try {
                OwnershipType ownershipType = OwnershipType.valueOf(filters.get("ownershipType").toUpperCase());
                spec = spec.and(StockSpecification.ownershipTypeEquals(ownershipType));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid ownership type value: {}", filters.get("ownershipType"));
            }
        }

        // ── PRICE ──────────────────────────────────────────────────────────────

        if (filters.containsKey("minPrice") || filters.containsKey("maxPrice")) {
            spec = spec.and(StockSpecification.priceBetween(
                    parseBigDecimal(filters, "minPrice"),
                    parseBigDecimal(filters, "maxPrice")
            ));
        }

        // ── 52-WEEK ────────────────────────────────────────────────────────────

        if (filters.containsKey("minCloseTo52WeekLow") || filters.containsKey("maxCloseTo52WeekLow")) {
            spec = spec.and(StockSpecification.closeTo52WeekLowPercentageBetween(
                    parseBigDecimal(filters, "minCloseTo52WeekLow"),
                    parseBigDecimal(filters, "maxCloseTo52WeekLow")
            ));
        }

        if (filters.containsKey("near52WeekLow")) {
            spec = spec.and(StockSpecification.near52WeekLow(parseBigDecimal(filters, "near52WeekLow")));
        }

        if (filters.containsKey("near52WeekHigh")) {
            spec = spec.and(StockSpecification.near52WeekHigh(parseBigDecimal(filters, "near52WeekHigh")));
        }

        // ── PROFIT MARGIN ──────────────────────────────────────────────────────

        if (filters.containsKey("minProfitMargin") || filters.containsKey("maxProfitMargin")) {
            spec = spec.and(StockSpecification.profitMarginBetween(
                    parseBigDecimal(filters, "minProfitMargin"),
                    parseBigDecimal(filters, "maxProfitMargin")
            ));
        }

        // ── MARGIN OF SAFETY ───────────────────────────────────────────────────

        if (filters.containsKey("minMarginOfSafety") || filters.containsKey("maxMarginOfSafety")) {
            spec = spec.and(StockSpecification.marginOfSafetyBetween(
                    parseBigDecimal(filters, "minMarginOfSafety"),
                    parseBigDecimal(filters, "maxMarginOfSafety")
            ));
        }

        // ── GRAHAM FAIR VALUE ──────────────────────────────────────────────────

        if (filters.containsKey("minGrahamFairValue") || filters.containsKey("maxGrahamFairValue")) {
            spec = spec.and(StockSpecification.grahamFairValueBetween(
                    parseBigDecimal(filters, "minGrahamFairValue"),
                    parseBigDecimal(filters, "maxGrahamFairValue")
            ));
        }

        // ── DEBT / EQUITY ──────────────────────────────────────────────────────

        if (filters.containsKey("minDebtToEquity") || filters.containsKey("maxDebtToEquity")) {
            spec = spec.and(StockSpecification.debtToEquityBetween(
                    parseBigDecimal(filters, "minDebtToEquity"),
                    parseBigDecimal(filters, "maxDebtToEquity")
            ));
        }

        // ── EPS ────────────────────────────────────────────────────────────────

        if (filters.containsKey("minEps") || filters.containsKey("maxEps")) {
            spec = spec.and(StockSpecification.epsBetween(
                    parseBigDecimal(filters, "minEps"),
                    parseBigDecimal(filters, "maxEps")
            ));
        }

        // ── BVPS ───────────────────────────────────────────────────────────────

        if (filters.containsKey("minBvps") || filters.containsKey("maxBvps")) {
            spec = spec.and(StockSpecification.bvpsBetween(
                    parseBigDecimal(filters, "minBvps"),
                    parseBigDecimal(filters, "maxBvps")
            ));
        }

        // ── PE RATIO ───────────────────────────────────────────────────────────

        if (filters.containsKey("minPeRatio") || filters.containsKey("maxPeRatio")) {
            spec = spec.and(StockSpecification.peRatioBetween(
                    parseBigDecimal(filters, "minPeRatio"),
                    parseBigDecimal(filters, "maxPeRatio")
            ));
        }

        // ── DIVIDEND YIELD ─────────────────────────────────────────────────────

        if (filters.containsKey("minDividendYield") || filters.containsKey("maxDividendYield")) {
            spec = spec.and(StockSpecification.dividendYieldBetween(
                    parseBigDecimal(filters, "minDividendYield"),
                    parseBigDecimal(filters, "maxDividendYield")
            ));
        }

        // ── BOOLEAN FLAGS ──────────────────────────────────────────────────────
        // Each flag is only applied when the key is present AND value is "true" or "false".
        // A missing key means "no filter". A value of "false" is intentionally ignored
        // because there is no negated spec (e.g. "not profitable") — extend StockSpecification
        // with notProfitable() etc. if you need those.

        Boolean profitable = parseBooleanFilter(filters, "profitable");
        if (Boolean.TRUE.equals(profitable)) {
            spec = spec.and(StockSpecification.profitable());
        }

        Boolean undervalued = parseBooleanFilter(filters, "undervalued");
        if (Boolean.TRUE.equals(undervalued)) {
            spec = spec.and(StockSpecification.undervalued());
        }

        Boolean overvalued = parseBooleanFilter(filters, "overvalued");
        if (Boolean.TRUE.equals(overvalued)) {
            spec = spec.and(StockSpecification.overvalued());
        }

        Boolean priceBelowGraham = parseBooleanFilter(filters, "priceBelowGrahamValue");
        if (Boolean.TRUE.equals(priceBelowGraham)) {
            spec = spec.and(StockSpecification.priceBelowGrahamValue());
        }

        Boolean priceAboveGraham = parseBooleanFilter(filters, "priceAboveGrahamValue");
        if (Boolean.TRUE.equals(priceAboveGraham)) {
            spec = spec.and(StockSpecification.priceAboveGrahamValue());
        }

        Boolean lowDebt = parseBooleanFilter(filters, "lowDebt");
        if (Boolean.TRUE.equals(lowDebt)) {
            spec = spec.and(StockSpecification.lowDebt());
        }

        Boolean highDebt = parseBooleanFilter(filters, "highDebt");
        if (Boolean.TRUE.equals(highDebt)) {
            spec = spec.and(StockSpecification.highDebt());
        }

        Boolean lowPeRatio = parseBooleanFilter(filters, "lowPeRatio");
        if (Boolean.TRUE.equals(lowPeRatio)) {
            spec = spec.and(StockSpecification.lowPeRatio());
        }

        Boolean highDividend = parseBooleanFilter(filters, "highDividend");
        if (Boolean.TRUE.equals(highDividend)) {
            spec = spec.and(StockSpecification.highDividend());
        }

        // ── INVESTOR PRESETS ───────────────────────────────────────────────────
        // Presets are only applied when explicitly set to "true".
        // "false" means "do not apply this preset filter" — not "exclude those stocks".

        Boolean valueInvestor = parseBooleanFilter(filters, "valueInvestorFavorites");
        if (Boolean.TRUE.equals(valueInvestor)) {
            spec = spec.and(StockSpecification.valueInvestorFavorites());
        }

        Boolean growthInvestor = parseBooleanFilter(filters, "growthInvestorFavorites");
        if (Boolean.TRUE.equals(growthInvestor)) {
            spec = spec.and(StockSpecification.growthInvestorFavorites());
        }

        Boolean incomeInvestor = parseBooleanFilter(filters, "incomeInvestorFavorites");
        if (Boolean.TRUE.equals(incomeInvestor)) {
            spec = spec.and(StockSpecification.incomeInvestorFavorites());
        }

        Boolean contrarian = parseBooleanFilter(filters, "contrarianFavorites");
        if (Boolean.TRUE.equals(contrarian)) {
            spec = spec.and(StockSpecification.contrarianFavorites());
        }

        Boolean graham = parseBooleanFilter(filters, "grahamCriteria");
        if (Boolean.TRUE.equals(graham)) {
            spec = spec.and(StockSpecification.grahamCriteria());
        }

        return spec;
    }
}

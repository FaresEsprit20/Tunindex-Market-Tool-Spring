package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.exception.EntityNotFoundException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.exception.InvalidEntityException;
import com.tunindex.market_tool.core.specification.StockSpecification;
import com.tunindex.market_tool.core.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.core.utils.pagination.PaginationUtil;
import com.tunindex.market_tool.core.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.domain.dto.providers.investingcom.StockDto;
import com.tunindex.market_tool.domain.entities.Stock;
import com.tunindex.market_tool.domain.entities.enums.OwnershipType;
import com.tunindex.market_tool.domain.entities.enums.SectorType;
import com.tunindex.market_tool.domain.repository.jpa.StockRepository;

import com.tunindex.market_tool.domain.services.stock.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    public PagedResponse<StockDto> findAllStocks(PaginationAndFilteringDto paginationDto) {
        log.info("📊 Finding all stocks with pagination: page={}, size={}, sortField={}, sortDirection={}",
                paginationDto.getPage(), paginationDto.getSize(),
                paginationDto.getSortField(), paginationDto.getSortDirection());

        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public StockDto findBySymbol(String symbol) {
        log.info("🔍 Finding stock by symbol: {}", symbol);

        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasLength(symbol)) {
            log.error("Stock symbol is null or empty");
            errors.add("Stock symbol is null or empty");
            throw new InvalidEntityException("Stock symbol is invalid", ErrorCodes.STOCK_NOT_VALID, errors);
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
            log.error("Stock symbol is null or empty");
            errors.add("Stock symbol is null or empty");
            throw new InvalidEntityException("Stock symbol is invalid", ErrorCodes.STOCK_NOT_VALID, errors);
        }

        if (!StringUtils.hasLength(exchange)) {
            log.error("Exchange is null or empty");
            errors.add("Exchange is null or empty");
            throw new InvalidEntityException("Exchange is invalid", ErrorCodes.STOCK_NOT_VALID, errors);
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

        Specification<Stock> specification = buildSpecificationFromFilters(paginationDto.getFilters());
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findUndervaluedStocks(PaginationAndFilteringDto paginationDto) {
        log.info("📈 Finding undervalued stocks (margin of safety > 0)");

        Specification<Stock> specification = StockSpecification.undervalued();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findOvervaluedStocks(PaginationAndFilteringDto paginationDto) {
        log.info("📉 Finding overvalued stocks (margin of safety < 0)");

        Specification<Stock> specification = StockSpecification.overvalued();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findStocksNear52WeekLow(PaginationAndFilteringDto paginationDto) {
        log.info("📉 Finding stocks near 52-week low");

        Specification<Stock> specification = StockSpecification.near52WeekLow(new BigDecimal("10"));
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findStocksNear52WeekHigh(PaginationAndFilteringDto paginationDto) {
        log.info("📈 Finding stocks near 52-week high");

        Specification<Stock> specification = StockSpecification.near52WeekHigh(new BigDecimal("90"));
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findUndervaluedByGraham(PaginationAndFilteringDto paginationDto) {
        log.info("📊 Finding stocks undervalued by Graham criteria (price < Graham fair value)");

        Specification<Stock> specification = StockSpecification.priceBelowGrahamValue();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findValueInvestorFavorites(PaginationAndFilteringDto paginationDto) {
        log.info("💎 Finding value investor favorites (MOS > 20%, profitable, low debt)");

        Specification<Stock> specification = StockSpecification.valueInvestorFavorites();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findGrowthInvestorFavorites(PaginationAndFilteringDto paginationDto) {
        log.info("🚀 Finding growth investor favorites (high profit margin, reasonable PE, positive MOS)");

        Specification<Stock> specification = StockSpecification.growthInvestorFavorites();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findIncomeInvestorFavorites(PaginationAndFilteringDto paginationDto) {
        log.info("💰 Finding income investor favorites (high dividend yield, profitable, low debt)");

        Specification<Stock> specification = StockSpecification.incomeInvestorFavorites();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findContrarianFavorites(PaginationAndFilteringDto paginationDto) {
        log.info("🎯 Finding contrarian favorites (near 52-week low but profitable)");

        Specification<Stock> specification = StockSpecification.contrarianFavorites();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockDto> searchByKeyword(String keyword) {
        log.info("🔎 Searching stocks by keyword: {}", keyword);

        if (!StringUtils.hasLength(keyword)) {
            log.warn("Search keyword is empty, returning empty list");
            return new ArrayList<>();
        }

        return stockRepository.searchByKeyword(keyword)
                .stream()
                .map(StockDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findMostActive(PaginationAndFilteringDto paginationDto) {
        log.info("📊 Finding most active stocks by volume");

        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findMostActive(pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findLargestMarketCap(PaginationAndFilteringDto paginationDto) {
        log.info("🏦 Finding largest market cap stocks");

        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findLargestMarketCap(pageable);

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
    @Transactional(readOnly = true)
    public List<Object[]> averagePeRatioBySector() {
        log.info("📈 Calculating average P/E ratio by sector");
        return stockRepository.averagePeRatioBySector();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Object[]> averageDividendYieldBySector() {
        log.info("💰 Calculating average dividend yield by sector");
        return stockRepository.averageDividendYieldBySector();
    }

    @Override
    @Transactional
    public void refreshStockData(String symbol) {
        log.info("🔄 Refreshing stock data for symbol: {}", symbol);

        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasLength(symbol)) {
            errors.add("Stock symbol is null or empty");
            throw new InvalidEntityException("Stock symbol is invalid", ErrorCodes.STOCK_NOT_VALID, errors);
        }

        boolean exists = stockRepository.existsBySymbol(symbol);
        if (!exists) {
            errors.add("Stock not found with symbol: " + symbol);
            throw new EntityNotFoundException("Stock not found", ErrorCodes.STOCK_NOT_FOUND, errors);
        }

        // Note: This would typically call the provider to fetch fresh data
        // But that's handled by the orchestrator

        log.info("✅ Stock refresh initiated for: {}", symbol);
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Builds a PagedResponse from a Page of Stock entities
     */
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
     * Builds a Specification from filter parameters
     */
    private Specification<Stock> buildSpecificationFromFilters(Map<String, String> filters) {
        Specification<Stock> spec = StockSpecification.empty();

        if (filters == null || filters.isEmpty()) {
            return spec;
        }

        // Symbol filter
        if (filters.containsKey("symbol")) {
            String symbol = filters.get("symbol");
            if (StringUtils.hasLength(symbol)) {
                spec = spec.and(StockSpecification.symbolContains(symbol));
            }
        }

        // Name filter
        if (filters.containsKey("name")) {
            String name = filters.get("name");
            if (StringUtils.hasLength(name)) {
                spec = spec.and(StockSpecification.nameContains(name));
            }
        }

        // Exchange filter
        if (filters.containsKey("exchange")) {
            String exchange = filters.get("exchange");
            if (StringUtils.hasLength(exchange)) {
                spec = spec.and(StockSpecification.exchangeEquals(exchange));
            }
        }

        // Sector filter
        if (filters.containsKey("sector")) {
            String sectorStr = filters.get("sector");
            if (StringUtils.hasLength(sectorStr)) {
                try {
                    SectorType sector = SectorType.valueOf(sectorStr.toUpperCase());
                    spec = spec.and(StockSpecification.sectorEquals(sector));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid sector value: {}", sectorStr);
                }
            }
        }

        // Ownership type filter
        if (filters.containsKey("ownershipType")) {
            String ownershipStr = filters.get("ownershipType");
            if (StringUtils.hasLength(ownershipStr)) {
                try {
                    OwnershipType ownershipType = OwnershipType.valueOf(ownershipStr.toUpperCase());
                    spec = spec.and(StockSpecification.ownershipTypeEquals(ownershipType));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid ownership type value: {}", ownershipStr);
                }
            }
        }

        // Price range filters
        if (filters.containsKey("minPrice")) {
            try {
                BigDecimal minPrice = new BigDecimal(filters.get("minPrice"));
                BigDecimal maxPrice = filters.containsKey("maxPrice") ? new BigDecimal(filters.get("maxPrice")) : null;
                spec = spec.and(StockSpecification.priceBetween(minPrice, maxPrice));
            } catch (NumberFormatException e) {
                log.warn("Invalid price filter value");
            }
        }

        // Profit margin range filters
        if (filters.containsKey("minProfitMargin")) {
            try {
                BigDecimal minMargin = new BigDecimal(filters.get("minProfitMargin"));
                BigDecimal maxMargin = filters.containsKey("maxProfitMargin") ? new BigDecimal(filters.get("maxProfitMargin")) : null;
                spec = spec.and(StockSpecification.profitMarginBetween(minMargin, maxMargin));
            } catch (NumberFormatException e) {
                log.warn("Invalid profit margin filter value");
            }
        }

        // Margin of safety range filters
        if (filters.containsKey("minMarginOfSafety")) {
            try {
                BigDecimal minMos = new BigDecimal(filters.get("minMarginOfSafety"));
                BigDecimal maxMos = filters.containsKey("maxMarginOfSafety") ? new BigDecimal(filters.get("maxMarginOfSafety")) : null;
                spec = spec.and(StockSpecification.marginOfSafetyBetween(minMos, maxMos));
            } catch (NumberFormatException e) {
                log.warn("Invalid margin of safety filter value");
            }
        }

        // Debt/Equity range filters
        if (filters.containsKey("minDebtToEquity")) {
            try {
                BigDecimal minDebt = new BigDecimal(filters.get("minDebtToEquity"));
                BigDecimal maxDebt = filters.containsKey("maxDebtToEquity") ? new BigDecimal(filters.get("maxDebtToEquity")) : null;
                spec = spec.and(StockSpecification.debtToEquityBetween(minDebt, maxDebt));
            } catch (NumberFormatException e) {
                log.warn("Invalid debt/equity filter value");
            }
        }

        // 52-week low percentage range filters
        if (filters.containsKey("minCloseTo52WeekLow")) {
            try {
                BigDecimal minPct = new BigDecimal(filters.get("minCloseTo52WeekLow"));
                BigDecimal maxPct = filters.containsKey("maxCloseTo52WeekLow") ? new BigDecimal(filters.get("maxCloseTo52WeekLow")) : null;
                spec = spec.and(StockSpecification.closeTo52WeekLowPercentageBetween(minPct, maxPct));
            } catch (NumberFormatException e) {
                log.warn("Invalid 52-week low percentage filter value");
            }
        }

        // PE ratio range filters
        if (filters.containsKey("minPeRatio")) {
            try {
                BigDecimal minPe = new BigDecimal(filters.get("minPeRatio"));
                BigDecimal maxPe = filters.containsKey("maxPeRatio") ? new BigDecimal(filters.get("maxPeRatio")) : null;
                spec = spec.and(StockSpecification.peRatioBetween(minPe, maxPe));
            } catch (NumberFormatException e) {
                log.warn("Invalid PE ratio filter value");
            }
        }

        // Dividend yield range filters
        if (filters.containsKey("minDividendYield")) {
            try {
                BigDecimal minYield = new BigDecimal(filters.get("minDividendYield"));
                BigDecimal maxYield = filters.containsKey("maxDividendYield") ? new BigDecimal(filters.get("maxDividendYield")) : null;
                spec = spec.and(StockSpecification.dividendYieldBetween(minYield, maxYield));
            } catch (NumberFormatException e) {
                log.warn("Invalid dividend yield filter value");
            }
        }

        return spec;
    }
}
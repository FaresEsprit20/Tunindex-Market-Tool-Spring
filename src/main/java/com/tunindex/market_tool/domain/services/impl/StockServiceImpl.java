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

        validatePaginationDto(paginationDto);

        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        // Using empty specification to get all stocks
        Page<Stock> stockPage = stockRepository.findAll(StockSpecification.empty(), pageable);

        return buildPagedResponse(stockPage);
    }

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
    public PagedResponse<StockDto> findUndervaluedStocks(PaginationAndFilteringDto paginationDto) {
        log.info("📈 Finding undervalued stocks (margin of safety > 0)");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.undervalued();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findOvervaluedStocks(PaginationAndFilteringDto paginationDto) {
        log.info("📉 Finding overvalued stocks (margin of safety < 0)");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.overvalued();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findStocksNear52WeekLow(PaginationAndFilteringDto paginationDto) {
        log.info("📉 Finding stocks near 52-week low");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.near52WeekLow(new BigDecimal("10"));
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findStocksNear52WeekHigh(PaginationAndFilteringDto paginationDto) {
        log.info("📈 Finding stocks near 52-week high");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.near52WeekHigh(new BigDecimal("90"));
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findUndervaluedByGraham(PaginationAndFilteringDto paginationDto) {
        log.info("📊 Finding stocks undervalued by Graham criteria (price < Graham fair value)");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.priceBelowGrahamValue();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findValueInvestorFavorites(PaginationAndFilteringDto paginationDto) {
        log.info("💎 Finding value investor favorites (MOS > 20%, profitable, low debt)");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.valueInvestorFavorites();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findGrowthInvestorFavorites(PaginationAndFilteringDto paginationDto) {
        log.info("🚀 Finding growth investor favorites (high profit margin, reasonable PE, positive MOS)");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.growthInvestorFavorites();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findIncomeInvestorFavorites(PaginationAndFilteringDto paginationDto) {
        log.info("💰 Finding income investor favorites (high dividend yield, profitable, low debt)");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.incomeInvestorFavorites();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findContrarianFavorites(PaginationAndFilteringDto paginationDto) {
        log.info("🎯 Finding contrarian favorites (near 52-week low but profitable)");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.contrarianFavorites();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findGrahamCriteriaStocks(PaginationAndFilteringDto paginationDto) {
        log.info("📚 Finding stocks meeting Graham criteria");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.grahamCriteria();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findMostActive(PaginationAndFilteringDto paginationDto) {
        log.info("📊 Finding most active stocks by volume");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.volumeGreaterThan(0L);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findLargestMarketCap(PaginationAndFilteringDto paginationDto) {
        log.info("🏦 Finding largest market cap stocks");

        validatePaginationDto(paginationDto);

        // Sort by market cap descending
        paginationDto.setSortField("fundamentalData.marketCap");
        paginationDto.setSortDirection(com.tunindex.market_tool.core.utils.pagination.enums.SortingDirection.DESC);

        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(StockSpecification.empty(), pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findHighestDividendYield(PaginationAndFilteringDto paginationDto) {
        log.info("💰 Finding highest dividend yield stocks");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.highDividend();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findBestMarginOfSafety(PaginationAndFilteringDto paginationDto) {
        log.info("🛡️ Finding best margin of safety stocks");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.undervalued();
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Stock> stockPage = stockRepository.findAll(specification, pageable);

        return buildPagedResponse(stockPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockDto> findLowestPERatio(PaginationAndFilteringDto paginationDto) {
        log.info("📐 Finding lowest P/E ratio stocks");

        validatePaginationDto(paginationDto);

        Specification<Stock> specification = StockSpecification.lowPeRatio();
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

    /**
     * Validates the pagination DTO
     */
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
        if (filters.containsKey("minPrice") || filters.containsKey("maxPrice")) {
            BigDecimal minPrice = filters.containsKey("minPrice") ? new BigDecimal(filters.get("minPrice")) : null;
            BigDecimal maxPrice = filters.containsKey("maxPrice") ? new BigDecimal(filters.get("maxPrice")) : null;
            spec = spec.and(StockSpecification.priceBetween(minPrice, maxPrice));
        }

        // Profit margin range filters
        if (filters.containsKey("minProfitMargin") || filters.containsKey("maxProfitMargin")) {
            BigDecimal minMargin = filters.containsKey("minProfitMargin") ? new BigDecimal(filters.get("minProfitMargin")) : null;
            BigDecimal maxMargin = filters.containsKey("maxProfitMargin") ? new BigDecimal(filters.get("maxProfitMargin")) : null;
            spec = spec.and(StockSpecification.profitMarginBetween(minMargin, maxMargin));
        }

        // Margin of safety range filters
        if (filters.containsKey("minMarginOfSafety") || filters.containsKey("maxMarginOfSafety")) {
            BigDecimal minMos = filters.containsKey("minMarginOfSafety") ? new BigDecimal(filters.get("minMarginOfSafety")) : null;
            BigDecimal maxMos = filters.containsKey("maxMarginOfSafety") ? new BigDecimal(filters.get("maxMarginOfSafety")) : null;
            spec = spec.and(StockSpecification.marginOfSafetyBetween(minMos, maxMos));
        }

        // Debt/Equity range filters
        if (filters.containsKey("minDebtToEquity") || filters.containsKey("maxDebtToEquity")) {
            BigDecimal minDebt = filters.containsKey("minDebtToEquity") ? new BigDecimal(filters.get("minDebtToEquity")) : null;
            BigDecimal maxDebt = filters.containsKey("maxDebtToEquity") ? new BigDecimal(filters.get("maxDebtToEquity")) : null;
            spec = spec.and(StockSpecification.debtToEquityBetween(minDebt, maxDebt));
        }

        // 52-week low percentage range filters
        if (filters.containsKey("minCloseTo52WeekLow") || filters.containsKey("maxCloseTo52WeekLow")) {
            BigDecimal minPct = filters.containsKey("minCloseTo52WeekLow") ? new BigDecimal(filters.get("minCloseTo52WeekLow")) : null;
            BigDecimal maxPct = filters.containsKey("maxCloseTo52WeekLow") ? new BigDecimal(filters.get("maxCloseTo52WeekLow")) : null;
            spec = spec.and(StockSpecification.closeTo52WeekLowPercentageBetween(minPct, maxPct));
        }

        // PE ratio range filters
        if (filters.containsKey("minPeRatio") || filters.containsKey("maxPeRatio")) {
            BigDecimal minPe = filters.containsKey("minPeRatio") ? new BigDecimal(filters.get("minPeRatio")) : null;
            BigDecimal maxPe = filters.containsKey("maxPeRatio") ? new BigDecimal(filters.get("maxPeRatio")) : null;
            spec = spec.and(StockSpecification.peRatioBetween(minPe, maxPe));
        }

        // Dividend yield range filters
        if (filters.containsKey("minDividendYield") || filters.containsKey("maxDividendYield")) {
            BigDecimal minYield = filters.containsKey("minDividendYield") ? new BigDecimal(filters.get("minDividendYield")) : null;
            BigDecimal maxYield = filters.containsKey("maxDividendYield") ? new BigDecimal(filters.get("maxDividendYield")) : null;
            spec = spec.and(StockSpecification.dividendYieldBetween(minYield, maxYield));
        }

        return spec;
    }
}
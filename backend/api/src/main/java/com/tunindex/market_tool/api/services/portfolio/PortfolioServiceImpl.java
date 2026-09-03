package com.tunindex.market_tool.api.services.portfolio;

import com.tunindex.market_tool.api.dto.portfolio.PortfolioPositionDto;
import com.tunindex.market_tool.api.dto.portfolio.PortfolioSummaryDto;
import com.tunindex.market_tool.api.dto.portfolio.PortfolioTransactionDto;
import com.tunindex.market_tool.api.dto.stock.StockResponseDto;
import com.tunindex.market_tool.api.entities.PortfolioAccount;
import com.tunindex.market_tool.api.entities.PortfolioPosition;
import com.tunindex.market_tool.api.entities.PortfolioTransaction;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.entities.enums.TransactionSide;
import com.tunindex.market_tool.api.repository.PortfolioAccountRepository;
import com.tunindex.market_tool.api.repository.PortfolioPositionRepository;
import com.tunindex.market_tool.api.repository.PortfolioTransactionRepository;
import com.tunindex.market_tool.api.repository.UserRepository;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private static final String COLLECTOR_URL = "http://collector-service/internal/stock-data";
    private static final int MONEY_SCALE = 3;

    private final PortfolioAccountRepository portfolioAccountRepository;
    private final PortfolioPositionRepository portfolioPositionRepository;
    private final PortfolioTransactionRepository portfolioTransactionRepository;
    private final UserRepository userRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    @Override
    public PortfolioSummaryDto getPortfolio(Authentication authentication) {
        User user = resolveUser(authentication);
        PortfolioAccount account = getOrCreateAccount(user);
        List<PortfolioPosition> positions = portfolioPositionRepository.findByAccountIdOrderBySymbolAsc(account.getId());

        List<PortfolioPositionDto> positionDtos = new ArrayList<>();
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        BigDecimal totalDayChangeValue = BigDecimal.ZERO;
        BigDecimal prevDayMarketValue = BigDecimal.ZERO;

        for (PortfolioPosition position : positions) {
            BigDecimal currentPrice = position.getAvgCostBasis();
            String name = position.getSymbol();
            BigDecimal prevClose = null;
            try {
                StockResponseDto stock = fetchStock(position.getSymbol());
                if (stock.getLastPrice() != null) {
                    currentPrice = stock.getLastPrice();
                }
                if (stock.getName() != null) {
                    name = stock.getName();
                }
                prevClose = stock.getPrevClose();
            } catch (Exception ex) {
                log.warn("Live price refresh failed for {} while building portfolio, falling back to cost basis", position.getSymbol());
            }

            BigDecimal marketValue = currentPrice.multiply(position.getQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal costBasisTotal = position.getAvgCostBasis().multiply(position.getQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal unrealizedPnl = marketValue.subtract(costBasisTotal);
            BigDecimal unrealizedPnlPct = costBasisTotal.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : unrealizedPnl.divide(costBasisTotal, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

            // Today's move on the position: (last - prevClose) x quantity.
            BigDecimal dayChangeValue = null;
            BigDecimal dayChangePct = null;
            if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal perShare = currentPrice.subtract(prevClose);
                dayChangeValue = perShare.multiply(position.getQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                dayChangePct = perShare.divide(prevClose, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                totalDayChangeValue = totalDayChangeValue.add(dayChangeValue);
                // Yesterday's value of the holding, so the portfolio-level
                // percentage is weighted by position size rather than a flat
                // average of each position's percentage.
                prevDayMarketValue = prevDayMarketValue.add(
                        prevClose.multiply(position.getQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            }

            totalMarketValue = totalMarketValue.add(marketValue);
            totalCostBasis = totalCostBasis.add(costBasisTotal);

            positionDtos.add(PortfolioPositionDto.builder()
                    .symbol(position.getSymbol())
                    .name(name)
                    .quantity(position.getQuantity())
                    .avgCostBasis(position.getAvgCostBasis())
                    .currentPrice(currentPrice)
                    .marketValue(marketValue)
                    .unrealizedPnl(unrealizedPnl)
                    .unrealizedPnlPct(unrealizedPnlPct)
                    .prevClose(prevClose)
                    .dayChangeValue(dayChangeValue)
                    .dayChangePct(dayChangePct)
                    .build());
        }

        BigDecimal totalUnrealizedPnl = totalMarketValue.subtract(totalCostBasis);
        BigDecimal totalUnrealizedPnlPct = totalCostBasis.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : totalUnrealizedPnl.divide(totalCostBasis, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        BigDecimal totalRealizedPnl = portfolioTransactionRepository.findByAccountIdOrderByExecutedAtDesc(account.getId())
                .stream()
                .map(PortfolioTransaction::getRealizedPnl)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPortfolioValue = account.getCashBalance().add(totalMarketValue);
        BigDecimal totalReturnPct = totalPortfolioValue.subtract(PortfolioAccount.STARTING_CASH)
                .divide(PortfolioAccount.STARTING_CASH, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return PortfolioSummaryDto.builder()
                .cashBalance(account.getCashBalance())
                .startingCash(PortfolioAccount.STARTING_CASH)
                .positions(positionDtos)
                .totalMarketValue(totalMarketValue)
                .totalPortfolioValue(totalPortfolioValue)
                .totalUnrealizedPnl(totalUnrealizedPnl)
                .totalUnrealizedPnlPct(totalUnrealizedPnlPct)
                .totalRealizedPnl(totalRealizedPnl)
                .totalDayChangeValue(totalDayChangeValue)
                .totalDayChangePct(prevDayMarketValue.compareTo(BigDecimal.ZERO) > 0
                        ? totalDayChangeValue.divide(prevDayMarketValue, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .totalReturnPct(totalReturnPct)
                .build();
    }

    @Override
    public List<PortfolioTransactionDto> getTransactions(Authentication authentication) {
        User user = resolveUser(authentication);
        PortfolioAccount account = getOrCreateAccount(user);
        return portfolioTransactionRepository.findByAccountIdOrderByExecutedAtDesc(account.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public PortfolioTransactionDto buy(Authentication authentication, String symbolRaw, BigDecimal quantity) {
        validateQuantity(quantity);
        User user = resolveUser(authentication);
        String symbol = symbolRaw.trim().toUpperCase();
        StockResponseDto stock = fetchStock(symbol);
        BigDecimal price = stock.getLastPrice();
        BigDecimal totalCost = price.multiply(quantity).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        PortfolioAccount account = getOrCreateAccount(user);
        if (account.getCashBalance().compareTo(totalCost) < 0) {
            throw new InvalidEntityException(
                    "Insufficient cash balance",
                    ErrorCodes.PORTFOLIO_INSUFFICIENT_FUNDS,
                    List.of("required: " + totalCost + ", available: " + account.getCashBalance()));
        }

        PortfolioPosition position = portfolioPositionRepository.findByAccountIdAndSymbol(account.getId(), symbol)
                .orElseGet(() -> PortfolioPosition.builder()
                        .account(account)
                        .symbol(symbol)
                        .quantity(BigDecimal.ZERO)
                        .avgCostBasis(BigDecimal.ZERO)
                        .build());

        BigDecimal existingCostTotal = position.getQuantity().multiply(position.getAvgCostBasis());
        BigDecimal newQty = position.getQuantity().add(quantity);
        BigDecimal newAvgCost = existingCostTotal.add(totalCost).divide(newQty, 6, RoundingMode.HALF_UP);

        position.setQuantity(newQty);
        position.setAvgCostBasis(newAvgCost);
        portfolioPositionRepository.save(position);

        account.setCashBalance(account.getCashBalance().subtract(totalCost));
        portfolioAccountRepository.save(account);

        PortfolioTransaction tx = PortfolioTransaction.builder()
                .account(account)
                .symbol(symbol)
                .side(TransactionSide.BUY)
                .quantity(quantity)
                .price(price)
                .totalAmount(totalCost)
                .build();
        portfolioTransactionRepository.save(tx);

        log.info("Portfolio BUY: user={} symbol={} qty={} price={}", user.getEmail(), symbol, quantity, price);
        return toDto(tx);
    }

    @Override
    @Transactional
    public PortfolioTransactionDto sell(Authentication authentication, String symbolRaw, BigDecimal quantity) {
        validateQuantity(quantity);
        User user = resolveUser(authentication);
        String symbol = symbolRaw.trim().toUpperCase();
        PortfolioAccount account = getOrCreateAccount(user);

        PortfolioPosition position = portfolioPositionRepository.findByAccountIdAndSymbol(account.getId(), symbol)
                .orElseThrow(() -> new InvalidEntityException(
                        "No position held in " + symbol,
                        ErrorCodes.PORTFOLIO_POSITION_NOT_FOUND,
                        Collections.singletonList("symbol: " + symbol)));

        if (position.getQuantity().compareTo(quantity) < 0) {
            throw new InvalidEntityException(
                    "Insufficient shares held",
                    ErrorCodes.PORTFOLIO_INSUFFICIENT_SHARES,
                    List.of("held: " + position.getQuantity() + ", requested: " + quantity));
        }

        StockResponseDto stock = fetchStock(symbol);
        BigDecimal price = stock.getLastPrice();
        BigDecimal proceeds = price.multiply(quantity).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal realizedPnl = price.subtract(position.getAvgCostBasis()).multiply(quantity).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal remainingQty = position.getQuantity().subtract(quantity);
        if (remainingQty.compareTo(BigDecimal.ZERO) == 0) {
            portfolioPositionRepository.delete(position);
        } else {
            position.setQuantity(remainingQty);
            portfolioPositionRepository.save(position);
        }

        account.setCashBalance(account.getCashBalance().add(proceeds));
        portfolioAccountRepository.save(account);

        PortfolioTransaction tx = PortfolioTransaction.builder()
                .account(account)
                .symbol(symbol)
                .side(TransactionSide.SELL)
                .quantity(quantity)
                .price(price)
                .totalAmount(proceeds)
                .realizedPnl(realizedPnl)
                .build();
        portfolioTransactionRepository.save(tx);

        log.info("Portfolio SELL: user={} symbol={} qty={} price={} realizedPnl={}", user.getEmail(), symbol, quantity, price, realizedPnl);
        return toDto(tx);
    }

    @Override
    @Transactional
    public PortfolioSummaryDto resetAccount(Authentication authentication) {
        User user = resolveUser(authentication);
        PortfolioAccount account = getOrCreateAccount(user);
        portfolioTransactionRepository.deleteByAccountId(account.getId());
        portfolioPositionRepository.deleteAll(portfolioPositionRepository.findByAccountIdOrderBySymbolAsc(account.getId()));
        account.setCashBalance(PortfolioAccount.STARTING_CASH);
        portfolioAccountRepository.save(account);
        log.info("Portfolio account reset for user={}", user.getEmail());
        return getPortfolio(authentication);
    }

    /**
     * Race-safe account lookup. The portfolio page issues its summary and
     * transactions requests concurrently, so on a user's very first visit
     * both can miss the account and both try to insert one — the second
     * then dies on the unique constraint over user_id and the page half
     * fails to load. saveAndFlush surfaces that conflict here (rather than
     * at commit, where it could not be handled) so the loser of the race
     * can simply read the row the winner just created.
     */
    private PortfolioAccount getOrCreateAccount(User user) {
        return portfolioAccountRepository.findByUserId(user.getId())
                .orElseGet(() -> createAccount(user));
    }

    private PortfolioAccount createAccount(User user) {
        try {
            return portfolioAccountRepository.saveAndFlush(PortfolioAccount.builder().user(user).build());
        } catch (DataIntegrityViolationException ex) {
            return portfolioAccountRepository.findByUserId(user.getId())
                    .orElseThrow(() -> ex);
        }
    }

    private StockResponseDto fetchStock(String symbol) {
        StockResponseDto stock = webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/symbol/{symbol}", symbol)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(StockResponseDto.class)
                .block();

        if (stock == null || stock.getLastPrice() == null) {
            throw new InvalidEntityException(
                    "Live price unavailable for " + symbol,
                    ErrorCodes.PORTFOLIO_PRICE_UNAVAILABLE,
                    Collections.singletonList("symbol: " + symbol));
        }
        return stock;
    }

    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidEntityException(
                    "Quantity must be a positive number",
                    ErrorCodes.PORTFOLIO_INVALID_QUANTITY,
                    Collections.singletonList("quantity: " + quantity));
        }
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidEntityException("Not authenticated", ErrorCodes.USER_NOT_AUTHENTICATED, Collections.emptyList());
        }
        String email = authentication.getName();
        return userRepository.findUserByEmail(email)
                .orElseThrow(() -> new InvalidEntityException(
                        "User not found", ErrorCodes.USER_NOT_FOUND, Collections.singletonList("email: " + email)));
    }

    private PortfolioTransactionDto toDto(PortfolioTransaction tx) {
        return PortfolioTransactionDto.builder()
                .id(tx.getId())
                .symbol(tx.getSymbol())
                .side(tx.getSide())
                .quantity(tx.getQuantity())
                .price(tx.getPrice())
                .totalAmount(tx.getTotalAmount())
                .realizedPnl(tx.getRealizedPnl())
                .executedAt(tx.getExecutedAt())
                .build();
    }
}

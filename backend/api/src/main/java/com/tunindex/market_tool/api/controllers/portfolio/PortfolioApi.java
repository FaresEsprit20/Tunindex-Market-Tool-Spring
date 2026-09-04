package com.tunindex.market_tool.api.controllers.portfolio;

import com.tunindex.market_tool.api.dto.portfolio.PortfolioAnalyticsDto;
import com.tunindex.market_tool.api.dto.portfolio.PortfolioSummaryDto;
import com.tunindex.market_tool.api.dto.portfolio.PortfolioTransactionDto;
import com.tunindex.market_tool.api.dto.portfolio.TradeRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@Tag(name = "Portfolio Simulator", description = "IBKR-style paper trading simulator scoped to Tunisian stocks")
public interface PortfolioApi {

    @GetMapping(value = APP_ROOT + "/portfolio", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the current user's simulated portfolio: cash, positions, live P&L")
    PortfolioSummaryDto getPortfolio(Authentication authentication);

    @GetMapping(value = APP_ROOT + "/portfolio/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Concentration, sector exposure, weighted beta and projected dividend income")
    PortfolioAnalyticsDto getAnalytics(Authentication authentication);

    @GetMapping(value = APP_ROOT + "/portfolio/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the current user's simulated trade history")
    List<PortfolioTransactionDto> getTransactions(Authentication authentication);

    @PostMapping(value = APP_ROOT + "/portfolio/buy", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buy shares of a Tunisian stock at the current real market price")
    PortfolioTransactionDto buy(@RequestBody TradeRequestDto request, Authentication authentication);

    @PostMapping(value = APP_ROOT + "/portfolio/sell", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Sell shares of a Tunisian stock at the current real market price")
    PortfolioTransactionDto sell(@RequestBody TradeRequestDto request, Authentication authentication);

    @PostMapping(value = APP_ROOT + "/portfolio/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reset the simulator: clear positions and transactions, restore starting cash")
    PortfolioSummaryDto resetAccount(Authentication authentication);
}

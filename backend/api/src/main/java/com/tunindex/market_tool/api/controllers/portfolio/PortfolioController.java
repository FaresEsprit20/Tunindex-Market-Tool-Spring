package com.tunindex.market_tool.api.controllers.portfolio;

import com.tunindex.market_tool.api.dto.portfolio.PortfolioSummaryDto;
import com.tunindex.market_tool.api.dto.portfolio.PortfolioTransactionDto;
import com.tunindex.market_tool.api.dto.portfolio.TradeRequestDto;
import com.tunindex.market_tool.api.services.portfolio.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PortfolioController implements PortfolioApi {

    private final PortfolioService portfolioService;

    @Override
    public PortfolioSummaryDto getPortfolio(Authentication authentication) {
        return portfolioService.getPortfolio(authentication);
    }

    @Override
    public List<PortfolioTransactionDto> getTransactions(Authentication authentication) {
        return portfolioService.getTransactions(authentication);
    }

    @Override
    public PortfolioTransactionDto buy(TradeRequestDto request, Authentication authentication) {
        return portfolioService.buy(authentication, request.getSymbol(), request.getQuantity());
    }

    @Override
    public PortfolioTransactionDto sell(TradeRequestDto request, Authentication authentication) {
        return portfolioService.sell(authentication, request.getSymbol(), request.getQuantity());
    }

    @Override
    public PortfolioSummaryDto resetAccount(Authentication authentication) {
        return portfolioService.resetAccount(authentication);
    }
}

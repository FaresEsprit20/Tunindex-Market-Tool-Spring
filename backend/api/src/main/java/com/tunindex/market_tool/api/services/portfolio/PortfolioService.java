package com.tunindex.market_tool.api.services.portfolio;

import com.tunindex.market_tool.api.dto.portfolio.PortfolioSummaryDto;
import com.tunindex.market_tool.api.dto.portfolio.PortfolioTransactionDto;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;

public interface PortfolioService {

    PortfolioSummaryDto getPortfolio(Authentication authentication);

    List<PortfolioTransactionDto> getTransactions(Authentication authentication);

    PortfolioTransactionDto buy(Authentication authentication, String symbol, BigDecimal quantity);

    PortfolioTransactionDto sell(Authentication authentication, String symbol, BigDecimal quantity);

    PortfolioSummaryDto resetAccount(Authentication authentication);
}

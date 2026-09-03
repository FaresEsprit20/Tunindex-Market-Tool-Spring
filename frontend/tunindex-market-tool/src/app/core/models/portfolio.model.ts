// Mirrors backend's PortfolioSummaryDto / PortfolioPositionDto /
// PortfolioTransactionDto (api module). Every number here is computed
// server-side from a real, freshly-fetched market price at trade time —
// see PortfolioServiceImpl.fetchStock — never a client-supplied price.

export type TransactionSide = 'BUY' | 'SELL';

export interface PortfolioPosition {
  symbol: string;
  name: string;
  quantity: number;
  avgCostBasis: number;
  currentPrice: number;
  marketValue: number;
  unrealizedPnl: number;
  unrealizedPnlPct: number;
  prevClose: number | null;
  dayChangeValue: number | null;
  dayChangePct: number | null;
}

export interface PortfolioSummary {
  cashBalance: number;
  startingCash: number;
  positions: PortfolioPosition[];
  totalMarketValue: number;
  totalPortfolioValue: number;
  totalUnrealizedPnl: number;
  totalUnrealizedPnlPct: number;
  totalRealizedPnl: number;
  totalReturnPct: number;
  totalDayChangeValue: number;
  totalDayChangePct: number;
}

export interface PortfolioTransaction {
  id: number;
  symbol: string;
  side: TransactionSide;
  quantity: number;
  price: number;
  totalAmount: number;
  realizedPnl: number | null;
  executedAt: string;
}

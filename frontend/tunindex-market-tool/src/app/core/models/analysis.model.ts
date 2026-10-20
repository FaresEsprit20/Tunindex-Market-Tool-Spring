// Mirrors backend's TechnicalAnalysisResponseDto / FundamentalAnalysisResponseDto.
// Every field is computed server-side from real data (see
// TechnicalAnalysisCalculator / FundamentalAnalysisCalculator on the
// collector) — not a scraped third-party score.

export type TrendSignal = 'BULLISH' | 'BEARISH' | 'NEUTRAL';
export type RsiSignal = 'OVERBOUGHT' | 'OVERSOLD' | 'NEUTRAL';
export type MacdCrossSignal = 'BULLISH_CROSS' | 'BEARISH_CROSS' | 'NONE';
export type OverallRating = 'STRONG' | 'MODERATE' | 'WEAK';
export type AdxSignal = 'STRONG_TREND' | 'WEAK_TREND' | 'NO_TREND';

export interface TechnicalAnalysis {
  dataPointsUsed: number;
  lastClose: number | null;

  sma20: number | null;
  sma50: number | null;
  trendSignal: TrendSignal;

  rsi14: number | null;
  rsiSignal: RsiSignal;

  macdLine: number | null;
  macdSignal: number | null;
  macdHistogram: number | null;
  macdCrossSignal: MacdCrossSignal;

  bollingerUpper: number | null;
  bollingerMiddle: number | null;
  bollingerLower: number | null;

  volatilityAnnualizedPct: number | null;

  stochasticK: number | null;
  stochasticD: number | null;
  stochasticSignal: RsiSignal;

  williamsR: number | null;
  williamsRSignal: RsiSignal;

  atr14: number | null;

  adx14: number | null;
  adxSignal: AdxSignal;
}

export interface FundamentalAnalysis {
  peRatio: number | null;
  sectorAvgPeRatio: number | null;
  dividendYield: number | null;
  sectorAvgDividendYield: number | null;
  debtToEquity: number | null;
  sectorAvgDebtToEquity: number | null;
  profitMargin: number | null;
  sectorAvgProfitMargin: number | null;
  priceToBook: number | null;
  sectorAvgPriceToBook: number | null;
  sectorPeerCount: number;

  valuationScore: number;
  profitabilityScore: number;
  financialHealthScore: number;
  incomeScore: number;
  overallScore: number;
  overallRating: OverallRating;
}

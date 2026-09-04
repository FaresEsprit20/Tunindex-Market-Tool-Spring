/**
 * Risk profile for one name, from GET /risk/metrics/{symbol}.
 *
 * Every figure can be null: the server refuses to compute a statistic it does
 * not have the sample size for. `methodology` carries the caveats in prose and
 * is meant to be rendered verbatim next to the numbers.
 */
export interface RiskMetrics {
  symbol: string;
  observations: number;
  periodStart: string | null;
  periodEnd: string | null;
  annualisedVolatilityPct: number | null;
  downsideDeviationPct: number | null;
  maxDrawdownPct: number | null;
  maxDrawdownPeak: string | null;
  maxDrawdownTrough: string | null;
  periodReturnPct: number | null;
  annualisedReturnPct: number | null;
  /** Against an equal-weighted market proxy — see methodology. */
  beta: number | null;
  /** R² of the beta regression: share of variance explained by the market. */
  varianceExplained: number | null;
  sharpeRatio: number | null;
  sortinoRatio: number | null;
  /** The hurdle the ratios used; shown so a bare ratio is never presented. */
  riskFreeRatePct: number | null;
  valueAtRisk95Pct: number | null;
  conditionalVar95Pct: number | null;
  bestDayPct: number | null;
  worstDayPct: number | null;
  positiveDaysPct: number | null;
  methodology: string[];
}

export interface CorrelationPair {
  symbolA: string;
  symbolB: string;
  correlation: number;
  overlap: number;
}

export interface CorrelationMatrix {
  symbols: string[];
  /** matrix[i][j]; null where the two names share too few trading days. */
  matrix: (number | null)[][];
  overlap: number[][];
  windowDays: number;
  minOverlap: number;
  mostDiversifying: CorrelationPair[];
  mostRedundant: CorrelationPair[];
}

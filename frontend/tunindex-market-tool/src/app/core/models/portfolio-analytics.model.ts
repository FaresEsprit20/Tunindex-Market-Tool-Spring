/**
 * Structural view of a portfolio from GET /portfolio/analytics: what it is
 * exposed to, rather than what it is worth.
 *
 * All weights are percentages of the invested book; cash is excluded from
 * them and reported separately as `cashWeightPct`.
 */
export interface PortfolioAnalytics {
  positionCount: number;
  totalMarketValue: number;
  cashBalance: number;
  cashWeightPct: number | null;
  /** Herfindahl index of position weights, 0..10000. */
  concentrationHhi: number | null;
  concentrationLabel: 'DIVERSIFIED' | 'MODERATE' | 'CONCENTRATED' | null;
  /** How many equally-weighted positions this book behaves like. */
  effectivePositions: number | null;
  largestPositionPct: number | null;
  largestPositionSymbol: string | null;
  largestSectorPct: number | null;
  largestSectorName: string | null;
  positionWeights: PortfolioWeight[];
  sectorWeights: PortfolioWeight[];
  /** Null when we hold a beta for too little of the book to average. */
  weightedBeta: number | null;
  betaCoveragePct: number | null;
  projectedAnnualIncome: number | null;
  portfolioYieldPct: number | null;
  incomeCoveragePct: number | null;
  incomeByPosition: PortfolioIncome[];
  /** Server-written notes, rendered verbatim. */
  observations: string[];
}

export interface PortfolioWeight {
  /** Symbol for a position row, sector enum name for a sector row. */
  key: string;
  label: string | null;
  marketValue: number;
  weightPct: number;
  positions: number;
}

export interface PortfolioIncome {
  symbol: string;
  name: string;
  quantity: number;
  marketValue: number;
  dividendYieldPct: number | null;
  projectedAnnualIncome: number | null;
}

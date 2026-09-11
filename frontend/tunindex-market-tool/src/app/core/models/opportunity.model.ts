// Mirrors the backend's OpportunityScoreResponseDto — the Tunindex Score.
// Every figure here is computed server-side by TunindexScorer from stored
// fundamentals, real price history and scraped headlines: a fixed weighted
// rule set, not a model and not a prediction.

import { TradeSetup } from './trade-setup.model';

export type Verdict = 'STRONG_BUY' | 'BUY' | 'WATCH' | 'HOLD' | 'AVOID';

export interface OpportunityScore {
  symbol: string;
  name: string;
  sector: string | null;
  lastPrice: number | null;
  currency: string | null;

  overallScore: number;
  verdict: Verdict;

  // Null means the inputs for that component were missing entirely — which
  // is different from scoring zero, and is rendered as "no data".
  valuationScore: number | null;
  financialHealthScore: number | null;
  timingScore: number | null;
  incomeScore: number | null;
  momentumScore: number | null;
  newsScore: number | null;

  /** 0-100: how much of the scorer's expected input actually existed. */
  dataCompleteness: number;

  reasons: string[];
  warnings: string[];

  /**
   * Where to buy it, from the Tradify Analyst.
   *
   * <p>Optional because an older backend will not send it, and a list that
   * breaks on a missing field is worse than one that quietly shows less.
   */
  tradeSetup?: TradeSetup | null;
}

export const VERDICT_LABELS: Record<Verdict, string> = {
  STRONG_BUY: 'Strong buy',
  BUY: 'Buy',
  WATCH: 'Watch',
  HOLD: 'Hold',
  AVOID: 'Avoid',
};

/** Component weights, kept in sync with TunindexScorer's constants. */
export const SCORE_COMPONENTS = [
  { key: 'valuationScore', label: 'Valuation', weight: 30 },
  { key: 'timingScore', label: 'Timing', weight: 25 },
  { key: 'financialHealthScore', label: 'Financial health', weight: 20 },
  { key: 'incomeScore', label: 'Income', weight: 10 },
  { key: 'momentumScore', label: 'Momentum', weight: 10 },
  { key: 'newsScore', label: 'News', weight: 5 },
] as const satisfies ReadonlyArray<{ key: keyof OpportunityScore; label: string; weight: number }>;


/** One day's recorded score, from the score_snapshots table. */
export interface ScoreHistoryPoint {
  date: string;
  overallScore: number;
  verdict: Verdict;
  closePrice: number | null;
}

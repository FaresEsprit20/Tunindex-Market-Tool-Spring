/**
 * Maps a scorer reason back to the screener filter that would find more
 * stocks like it.
 *
 * <p>The scorer already explains itself in prose; this makes the prose
 * operable, so reading "trades 36% below Graham fair value" and exploring
 * everything else that does are the same gesture. The user then learns one
 * vocabulary instead of two.
 *
 * <p>Matched on the phrasing TunindexScorer emits. Reasons with no screener
 * equivalent (RSI levels, headline counts, one-year returns) return null and
 * render as plain text — inventing a filter that doesn't exist would be
 * worse than leaving them inert.
 */
export interface ReasonFilter {
  /** Query param the stock list understands. */
  param: string;
  value: string;
  /** What the resulting screen shows, for the link's title attribute. */
  description: string;
}

const RULES: { test: RegExp; filter: ReasonFilter }[] = [
  {
    test: /below its Graham fair value/i,
    filter: { param: 'undervalued', value: 'true', description: 'All stocks trading below Graham fair value' },
  },
  {
    test: /^P\/E of/i,
    filter: { param: 'lowPeRatio', value: 'true', description: 'All stocks with a P/E under 15' },
  },
  {
    test: /52-week low/i,
    filter: { param: 'near52WeekLow', value: 'true', description: 'All stocks near their 52-week low' },
  },
  {
    test: /Low leverage/i,
    filter: { param: 'lowDebt', value: 'true', description: 'All stocks with debt/equity under 0.5' },
  },
  {
    test: /Dividend yield/i,
    filter: { param: 'highDividend', value: 'true', description: 'All stocks yielding over 4%' },
  },
  {
    test: /Trades .* book value/i,
    filter: { param: 'priceBelowGrahamValue', value: 'true', description: 'All stocks priced below Graham value' },
  },
];

export function reasonToFilter(reason: string): ReasonFilter | null {
  return RULES.find((rule) => rule.test.test(reason))?.filter ?? null;
}

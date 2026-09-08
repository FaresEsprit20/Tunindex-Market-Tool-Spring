/**
 * Tunisian macroeconomic backdrop, from GET /market/macro.
 *
 * Three groups, kept apart because they are not the same kind of number:
 * `rates` is a monthly central-bank figure, `economy` is annual and can lag by
 * a year, and `currencies` are live quotes that move through the day.
 */
export interface MacroSnapshot {
  rates: MacroIndicator[];
  economy: MacroIndicator[];
  currencies: MarketQuote[];
  fetchedAt: string | null;
  /** Publishers unreachable on the last attempt — render this, do not hide it. */
  unavailable: string[];
}

export interface MacroIndicator {
  key: 'TMM' | 'INFLATION_CPI' | 'EXTERNAL_DEBT_USD' | string;
  label: string;
  /** What the figure means for an equity investor. */
  note: string | null;
  value: number | null;
  /** "%" or "USD" — the debt figure is money, not a percentage. */
  unit: string;
  /** The publisher's own wording for the period; always show it. */
  periodLabel: string | null;
  source: string;
  sourceUrl: string;
}

/** One traded instrument: a metal, a crypto pair, or a currency cross. */
export interface MarketQuote {
  key: 'GOLD' | 'SILVER' | 'BTC' | 'ETH' | 'SOL' | 'USD_TND' | 'EUR_TND' | string;
  label: string;
  symbol: string;
  price: number;
  previousClose: number | null;
  changePct: number | null;
  changeValue: number | null;
  currency: string;
  category: 'METAL' | 'CRYPTO' | 'FX';
  /**
   * What the change is measured against. Metals and currencies have a daily
   * close; crypto trades continuously and quotes a rolling window instead.
   * Shown in the tooltip so the two are never conflated.
   */
  changeBasis: 'PREVIOUS_CLOSE' | 'ROLLING_24H' | string;
  fetchedAt: string | null;
}

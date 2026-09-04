/**
 * Tunisian macroeconomic backdrop, from GET /market/macro.
 *
 * Rates come from the Banque Centrale de Tunisie and are published
 * continuously; `economy` comes from the World Bank and is annual, so it can
 * lag by a year. They are kept apart because they are not comparable series.
 */
export interface MacroSnapshot {
  rates: MacroIndicator[];
  economy: MacroIndicator[];
  fetchedAt: string | null;
  /** Publishers unreachable on the last attempt — render this, do not hide it. */
  unavailable: string[];
}

export interface MacroIndicator {
  key:
    | 'POLICY_RATE'
    | 'MONEY_MARKET_RATE'
    | 'TMM'
    | 'SAVINGS_RATE'
    | 'INFLATION_CPI'
    | 'GDP_GROWTH';
  label: string;
  /** What the figure means for an equity investor. */
  note: string | null;
  value: number | null;
  unit: string;
  /** The publisher's own wording for the period; always show it. */
  periodLabel: string | null;
  source: string;
  sourceUrl: string;
}

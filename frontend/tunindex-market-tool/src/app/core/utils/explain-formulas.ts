import { StockDto } from '../models/stock.model';

/**
 * Builds the arithmetic line shown behind each derived figure.
 *
 * <p>These reproduce the definitions the collector computes with, so the
 * popover shows the same division the backend performed rather than a
 * textbook restatement of it. Returns an empty string when an input is
 * missing — the directive treats that as "no popover", which is correct:
 * a formula with a blank in it explains nothing.
 */

const fmt = (value: number | null | undefined, decimals = 2): string =>
  value === null || value === undefined ? '' : value.toFixed(decimals);

export function explainPeRatio(stock: StockDto): string {
  if (stock.peRatio === null || stock.lastPrice === null || !stock.eps) return '';
  return `${fmt(stock.peRatio)} = ${fmt(stock.lastPrice)} ÷ ${fmt(stock.eps)}`;
}

export function explainPriceToBook(stock: StockDto): string {
  if (stock.priceToBook === null || stock.lastPrice === null || !stock.bookValuePerShare) return '';
  return `${fmt(stock.priceToBook)} = ${fmt(stock.lastPrice)} ÷ ${fmt(stock.bookValuePerShare)}`;
}

export function explainMarginOfSafety(stock: StockDto): string {
  if (stock.marginOfSafety === null || stock.grahamFairValue === null || stock.lastPrice === null) return '';
  return `${fmt(stock.marginOfSafety, 1)}% = (${fmt(stock.grahamFairValue)} − ${fmt(stock.lastPrice)}) ÷ ${fmt(stock.grahamFairValue)}`;
}

export function explainDayChange(stock: StockDto): string {
  if (stock.lastPrice === null || !stock.prevClose) return '';
  const pct = ((stock.lastPrice - stock.prevClose) / stock.prevClose) * 100;
  return `${pct >= 0 ? '+' : ''}${fmt(pct)}% = (${fmt(stock.lastPrice)} − ${fmt(stock.prevClose)}) ÷ ${fmt(stock.prevClose)}`;
}

export function explainPositionIn52Week(stock: StockDto): string {
  if (stock.closeTo52weekslowPct === null || stock.week52High === null || stock.week52Low === null
      || stock.lastPrice === null) {
    return '';
  }
  return `${fmt(stock.closeTo52weekslowPct, 1)}% = (${fmt(stock.week52High)} − ${fmt(stock.lastPrice)}) ÷ (${fmt(stock.week52High)} − ${fmt(stock.week52Low)})`;
}

/** Short notes explaining what a figure means, shown under the arithmetic. */
export const EXPLAIN_NOTES = {
  peRatio: 'Price per unit of annual earnings. Lower is cheaper for the same earnings.',
  priceToBook: 'Price against net assets per share. Below 1 means it trades under book value.',
  marginOfSafety: "How far below Graham's fair value it trades. Positive means a discount.",
  dividendYield: 'Annual dividend as a percentage of the current price.',
  debtToEquity: 'Borrowings against shareholder equity. Under 0.5 is conservative.',
  profitMargin: 'Share of revenue that becomes profit.',
  grahamFairValue: "Graham's intrinsic-value formula, from earnings and book value per share.",
  bookValuePerShare: 'Net assets divided by shares outstanding.',
  position52Week: '100% means it sits at the 52-week low, 0% at the high.',
} as const;

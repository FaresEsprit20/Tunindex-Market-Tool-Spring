// Mirrors backend's PriceHistoryPointResponseDto — real daily OHLCV,
// scraped from ilboursa.com's quote-download CSV export.
export interface PriceHistoryPoint {
  tradeDate: string;
  open: number | null;
  high: number | null;
  low: number | null;
  close: number | null;
  volume: number | null;
}

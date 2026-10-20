// Mirrors backend's StockNewsResponseDto — real headlines scraped from
// ilboursa.com's per-stock news feed (see IlBoursaNewsProvider).
export interface StockNews {
  headline: string;
  url: string;
  publishedAt: string;
}

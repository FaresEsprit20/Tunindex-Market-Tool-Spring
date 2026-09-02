// Mirrors backend's NewsImpactResponseDto — a real headline paired with a
// transparent, keyword-based sentiment tag (see NewsSentimentClassifier,
// a fixed list, not a model) and the real subsequent price move from the
// same scraped PriceHistory used everywhere else in the app.

export type NewsSentiment = 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';

export interface NewsImpact {
  headline: string;
  url: string;
  publishedAt: string;

  sentiment: NewsSentiment;
  matchedKeywords: string[];

  priceBeforeDate: string | null;
  priceBeforeClose: number | null;
  priceAfterDate: string | null;
  priceAfterClose: number | null;
  priceChangePct: number | null;
  priceMoveMatchesSentiment: boolean | null;
}

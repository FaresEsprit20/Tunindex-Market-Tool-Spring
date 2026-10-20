// Trading session and market-wide news. Mirrors the backend's
// MarketSessionResponseDto / MarketNewsResponseDto.

export type SessionState = 'PRE_OPEN' | 'OPEN' | 'PRE_CLOSE' | 'CLOSED' | 'WEEKEND';

export interface MarketSession {
  state: SessionState;
  label: string;
  nextTransitionLabel: string;
  nextTransitionAt: string;
  secondsUntilTransition: number;
  tunisTime: string;
  timezone: string;
  /** Always true: derived from the published timetable, not a live feed. */
  scheduleBased: boolean;
}

export interface MarketNewsItem {
  headline: string;
  url: string;
  publishedAt: string;
  relatedPrice: number | null;
  relatedChangePct: number | null;
  sentiment: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL' | null;
}

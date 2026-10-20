/**
 * Whole-market summary from GET /market/breadth.
 *
 * Nullable numbers are null when the server could not compute them honestly
 * — never 0. Rendering must distinguish the two: a sector with no priced
 * name reads "no data", not "flat".
 */
export interface MarketBreadth {
  advancing: number;
  declining: number;
  unchanged: number;
  /** Names with no computable move: missing last price or previous close. */
  notPriced: number;
  total: number;
  averageChangePct: number | null;
  totalVolume: number | null;
  topGainers: MarketMover[];
  topLosers: MarketMover[];
  mostActive: MarketMover[];
  sectorPerformance: SectorPerformance[];
  asOf: string | null;
}

export interface MarketMover {
  symbol: string;
  name: string;
  sector: string | null;
  exchange: string | null;
  lastPrice: number | null;
  prevClose: number | null;
  changePct: number;
  volume: number | null;
}

export interface SectorPerformance {
  sector: string;
  /** Equal-weighted; null when nothing in the sector could be priced. */
  averageChangePct: number | null;
  advancing: number;
  declining: number;
  /** Sample size behind the average. */
  priced: number;
  total: number;
}

/** One name flagged by GET /market/unusual, with its evidence attached. */
export interface UnusualActivity {
  symbol: string;
  name: string;
  sector: string | null;
  signal: 'VOLUME_SPIKE' | 'BREAKOUT_52W_HIGH' | 'BREAKDOWN_52W_LOW' | 'LARGE_MOVE' | 'WIDE_RANGE';
  detail: string;
  strength: number;
  lastPrice: number | null;
  changePct: number | null;
  volume: number | null;
  avgVolume3m: number | null;
  volumeMultiple: number | null;
  week52High: number | null;
  week52Low: number | null;
}

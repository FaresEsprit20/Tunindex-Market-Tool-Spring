// Mirrors backend/collector's StockDto field-for-field (see StockDto.java).
// Named StockDto (not Stock) to avoid colliding with the Stock service class.

export type SectorType =
  | 'FINANCIALS'
  | 'BANKING'
  | 'INSURANCE'
  | 'TECHNOLOGY'
  | 'INDUSTRIALS'
  | 'MATERIALS'
  | 'CONSUMER_GOODS'
  | 'TELECOM'
  | 'ENERGY'
  | 'HEALTHCARE'
  | 'REAL_ESTATE'
  | 'UTILITIES'
  | 'OTHER';

export type OwnershipType = 'PRIVATE' | 'GOVERNMENT';

export const SECTOR_LABELS: Record<SectorType, string> = {
  FINANCIALS: 'Financials',
  BANKING: 'Banking Services',
  INSURANCE: 'Insurance',
  TECHNOLOGY: 'Technology',
  INDUSTRIALS: 'Industrials',
  MATERIALS: 'Materials',
  CONSUMER_GOODS: 'Consumer Goods',
  TELECOM: 'Telecommunications',
  ENERGY: 'Energy',
  HEALTHCARE: 'Healthcare',
  REAL_ESTATE: 'Real Estate',
  UTILITIES: 'Utilities',
  OTHER: 'Other',
};

export const OWNERSHIP_LABELS: Record<OwnershipType, string> = {
  PRIVATE: 'Private Sector',
  GOVERNMENT: 'Government Owned',
};

export interface StockDto {
  id: number;
  symbol: string;
  name: string;
  url: string | null;
  exchange: string;
  exchangeFullName: string | null;
  market: string | null;
  currency: string | null;
  sector: SectorType;
  industry: string | null;
  ownershipType: OwnershipType;

  // Price
  lastPrice: number | null;
  prevClose: number | null;
  dayHigh: number | null;
  dayLow: number | null;
  week52High: number | null;
  week52Low: number | null;
  week52Range: string | null;
  closeTo52weekslowPct: number | null;

  // Volume
  volume: number | null;
  avgVolume3m: number | null;

  // Fundamentals
  marketCap: number | null;
  sharesOutstanding: number | null;
  eps: number | null;
  peRatio: number | null;
  dividendYield: number | null;
  revenue: number | null;
  oneYearReturn: number | null;

  // Ratios
  priceToBook: number | null;
  debtToEquity: number | null;
  profitMargin: number | null;
  payoutRatio: number | null;

  // Technical
  beta: number | null;

  // Calculated (Graham valuation)
  grahamFairValue: number | null;
  marginOfSafety: number | null;
  bookValuePerShare: number | null;

  lastUpdate: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

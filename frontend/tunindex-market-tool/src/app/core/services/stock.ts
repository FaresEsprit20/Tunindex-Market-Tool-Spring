import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { OwnershipStatEntry, SectorStatEntry } from '../models/market-statistics.model';
import { PagedResponse, PaginationAndFilteringRequest } from '../models/pagination.model';
import { StockDto } from '../models/stock.model';
import { PriceHistoryPoint } from '../models/price-history.model';
import { FundamentalAnalysis, TechnicalAnalysis } from '../models/analysis.model';

/**
 * Keys accepted by the backend's filters map (collector's StockServiceImpl
 * buildSpecification), confirmed by reading the actual key-by-key mapping —
 * every key not listed here is silently ignored server-side, not rejected.
 */
export interface StockFilters {
  symbol?: string;
  name?: string;
  exchange?: string;
  sector?: string;
  ownershipType?: string;
  minPrice?: string;
  maxPrice?: string;
  minCloseTo52WeekLow?: string;
  maxCloseTo52WeekLow?: string;
  near52WeekLow?: 'true';
  near52WeekHigh?: 'true';
  minProfitMargin?: string;
  maxProfitMargin?: string;
  minMarginOfSafety?: string;
  maxMarginOfSafety?: string;
  minGrahamFairValue?: string;
  maxGrahamFairValue?: string;
  minDebtToEquity?: string;
  maxDebtToEquity?: string;
  minEps?: string;
  maxEps?: string;
  minBvps?: string;
  maxBvps?: string;
  minPeRatio?: string;
  maxPeRatio?: string;
  minDividendYield?: string;
  maxDividendYield?: string;
  // Boolean flags — "true" applies the filter, anything else (including
  // "false") is treated as "no filter", per the backend's own comment.
  profitable?: 'true';
  undervalued?: 'true';
  overvalued?: 'true';
  priceBelowGrahamValue?: 'true';
  priceAboveGrahamValue?: 'true';
  lowDebt?: 'true';
  highDebt?: 'true';
  lowPeRatio?: 'true';
  highDividend?: 'true';
  // Investor presets — mutually exclusive by convention, not enforced.
  valueInvestorFavorites?: 'true';
  growthInvestorFavorites?: 'true';
  incomeInvestorFavorites?: 'true';
  contrarianFavorites?: 'true';
  grahamCriteria?: 'true';
}

@Injectable({ providedIn: 'root' })
export class Stock {
  private readonly http = inject(HttpClient);

  findBySymbol(symbol: string): Observable<StockDto> {
    return this.http.get<StockDto>(`${API_BASE_URL}/symbol/${encodeURIComponent(symbol)}`);
  }

  findBySymbolAndExchange(symbol: string, exchange: string): Observable<StockDto> {
    return this.http.get<StockDto>(
      `${API_BASE_URL}/symbol/${encodeURIComponent(symbol)}/exchange/${encodeURIComponent(exchange)}`,
    );
  }

  filter(
    request: Omit<PaginationAndFilteringRequest, 'filters'> & { filters?: StockFilters },
  ): Observable<PagedResponse<StockDto>> {
    return this.http.post<PagedResponse<StockDto>>(`${API_BASE_URL}/filter`, request);
  }

  countBySector(): Observable<SectorStatEntry[]> {
    return this.http.get<SectorStatEntry[]>(`${API_BASE_URL}/statistics/by-sector`);
  }

  countByOwnership(): Observable<OwnershipStatEntry[]> {
    return this.http.get<OwnershipStatEntry[]>(`${API_BASE_URL}/statistics/by-ownership`);
  }

  refresh(symbol: string): Observable<void> {
    return this.http.put<void>(`${API_BASE_URL}/refresh/${encodeURIComponent(symbol)}`, {});
  }

  /**
   * Real daily OHLCV history, scraped from ilboursa.com — see
   * IlBoursaHistoryProvider on the collector. Slow (a few seconds) the very
   * first time a symbol has nothing cached yet, since that triggers a live
   * scrape; fast on every call after that.
   */
  getHistory(symbol: string, days = 180): Observable<PriceHistoryPoint[]> {
    return this.http.get<PriceHistoryPoint[]>(`${API_BASE_URL}/history/${encodeURIComponent(symbol)}`, {
      params: { days },
    });
  }

  getTechnicalAnalysis(symbol: string, days = 180): Observable<TechnicalAnalysis> {
    return this.http.get<TechnicalAnalysis>(`${API_BASE_URL}/analysis/${encodeURIComponent(symbol)}/technical`, {
      params: { days },
    });
  }

  getFundamentalAnalysis(symbol: string): Observable<FundamentalAnalysis> {
    return this.http.get<FundamentalAnalysis>(`${API_BASE_URL}/analysis/${encodeURIComponent(symbol)}/fundamental`);
  }
}

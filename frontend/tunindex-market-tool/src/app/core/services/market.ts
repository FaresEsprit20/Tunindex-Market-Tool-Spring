import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { MarketNewsItem, MarketSession } from '../models/market.model';
import { MarketBreadth, UnusualActivity } from '../models/market-breadth.model';

@Injectable({ providedIn: 'root' })
export class Market {
  private readonly http = inject(HttpClient);

  /**
   * Where the BVMT trading day stands right now. Derived server-side from
   * the published timetable in Africa/Tunis — see MarketSessionService.
   */
  getSession(): Observable<MarketSession> {
    return this.http.get<MarketSession>(`${API_BASE_URL}/market/session`);
  }

  /** Market-wide headlines scraped from the exchange news feed. */
  getNews(limit = 15): Observable<MarketNewsItem[]> {
    return this.http.get<MarketNewsItem[]>(`${API_BASE_URL}/market/news`, { params: { limit } });
  }

  /**
   * Advancers, decliners, movers and sector performance across every name we
   * track. Computed server-side on each request from the stored quotes, so
   * this reflects the same prices the grid is showing.
   */
  getBreadth(): Observable<MarketBreadth> {
    return this.http.get<MarketBreadth>(`${API_BASE_URL}/market/breadth`);
  }

  /** Names trading unlike themselves today, ranked by how far out of line. */
  getUnusualActivity(limit = 20): Observable<UnusualActivity[]> {
    return this.http.get<UnusualActivity[]>(`${API_BASE_URL}/market/unusual`, { params: { limit } });
  }
}

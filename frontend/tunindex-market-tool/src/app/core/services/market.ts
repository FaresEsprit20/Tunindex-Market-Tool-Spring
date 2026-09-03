import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { MarketNewsItem, MarketSession } from '../models/market.model';

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
}

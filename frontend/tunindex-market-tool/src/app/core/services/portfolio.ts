import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { PortfolioSummary, PortfolioTransaction } from '../models/portfolio.model';

/**
 * IBKR-style paper trading simulator, scoped to Tunisian (BVMT) stocks.
 * Every trade executes at a real price fetched server-side at the moment
 * of the call (see PortfolioServiceImpl.fetchStock on the api module) —
 * the client only ever sends a symbol and a quantity, never a price.
 */
@Injectable({ providedIn: 'root' })
export class Portfolio {
  private readonly http = inject(HttpClient);

  getPortfolio(): Observable<PortfolioSummary> {
    return this.http.get<PortfolioSummary>(`${API_BASE_URL}/portfolio`);
  }

  getTransactions(): Observable<PortfolioTransaction[]> {
    return this.http.get<PortfolioTransaction[]>(`${API_BASE_URL}/portfolio/transactions`);
  }

  buy(symbol: string, quantity: number): Observable<PortfolioTransaction> {
    return this.http.post<PortfolioTransaction>(`${API_BASE_URL}/portfolio/buy`, { symbol, quantity });
  }

  sell(symbol: string, quantity: number): Observable<PortfolioTransaction> {
    return this.http.post<PortfolioTransaction>(`${API_BASE_URL}/portfolio/sell`, { symbol, quantity });
  }

  reset(): Observable<PortfolioSummary> {
    return this.http.post<PortfolioSummary>(`${API_BASE_URL}/portfolio/reset`, {});
  }
}

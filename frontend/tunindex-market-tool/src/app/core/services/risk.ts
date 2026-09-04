import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { CorrelationMatrix, RiskMetrics } from '../models/risk.model';

/**
 * Risk statistics computed server-side from stored daily closes.
 *
 * No client-side maths lives here on purpose: volatility, beta and
 * correlation are computed once in the collector so the figure on a stock
 * page and the figure in a portfolio view can never disagree.
 */
@Injectable({ providedIn: 'root' })
export class Risk {
  private readonly http = inject(HttpClient);

  /** Volatility, drawdown, beta, Sharpe and VaR for one symbol. */
  getMetrics(symbol: string, windowDays = 365): Observable<RiskMetrics> {
    return this.http.get<RiskMetrics>(`${API_BASE_URL}/risk/metrics/${symbol}`, {
      params: { windowDays },
    });
  }

  /** Pairwise return correlation across a set of names. */
  getCorrelation(symbols: string[], windowDays = 365): Observable<CorrelationMatrix> {
    return this.http.get<CorrelationMatrix>(`${API_BASE_URL}/risk/correlation`, {
      params: { symbols: symbols.join(','), windowDays },
    });
  }
}

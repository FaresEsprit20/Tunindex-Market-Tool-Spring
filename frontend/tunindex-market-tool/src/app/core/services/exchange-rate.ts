import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { ExchangeRates } from '../models/exchange-rate.model';

@Injectable({ providedIn: 'root' })
export class ExchangeRate {
  private readonly http = inject(HttpClient);

  getRates(): Observable<ExchangeRates> {
    return this.http.get<ExchangeRates>(`${API_BASE_URL}/exchange-rates`);
  }
}

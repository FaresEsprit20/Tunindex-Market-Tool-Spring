// Mirrors backend's ExchangeRateResponseDto / CurrencyRateResponseDto
// (api module) — live rates from exchangerate-api.com's free endpoint,
// fetched server-side by the collector and cached for an hour.

export interface CurrencyRate {
  code: string;
  name: string;
  /** TND value of 1 unit of this currency. */
  rateToTnd: number;
}

export interface ExchangeRates {
  baseCurrency: string;
  rates: CurrencyRate[];
  lastUpdated: string;
}

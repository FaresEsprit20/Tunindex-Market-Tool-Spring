/**
 * ISO 3166 alpha-2 country for the exchange a symbol trades on, keyed by
 * exchange name as the collector stores it. Consumed by
 * {@link CountryFlag}, which draws the flag as inline SVG — emoji flags are
 * unusable here because Windows renders regional-indicator pairs as bare
 * letters rather than a flag.
 */
const EXCHANGE_COUNTRY: Record<string, string> = {
  'tunis stock exchange': 'TN',
  bvmt: 'TN',
  'bourse de tunis': 'TN',
  'casablanca stock exchange': 'MA',
  'algiers stock exchange': 'DZ',
  'egyptian exchange': 'EG',
  euronext: 'EU',
  nasdaq: 'US',
  nyse: 'US',
  lse: 'GB',
  'london stock exchange': 'GB',
};

/** Falls back to a neutral placeholder rather than an empty cell. */
export function exchangeCountry(exchange: string | null | undefined): string {
  if (!exchange) {
    return '??';
  }
  return EXCHANGE_COUNTRY[exchange.trim().toLowerCase()] ?? exchange.trim().slice(0, 2).toUpperCase();
}

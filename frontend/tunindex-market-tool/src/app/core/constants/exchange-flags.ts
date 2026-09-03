/**
 * Country flag for the exchange a symbol trades on.
 *
 * <p>Keyed by exchange name as the collector stores it (the BVMT rows all
 * read "Tunis Stock Exchange"), with a few regional venues listed so the
 * mapping doesn't need revisiting the first time a second exchange
 * appears. Emoji rather than images: nothing to request, nothing to 404.
 */
const EXCHANGE_FLAGS: Record<string, string> = {
  'tunis stock exchange': '🇹🇳',
  bvmt: '🇹🇳',
  'casablanca stock exchange': '🇲🇦',
  'egyptian exchange': '🇪🇬',
  'bourse de tunis': '🇹🇳',
  euronext: '🇪🇺',
  nasdaq: '🇺🇸',
  nyse: '🇺🇸',
  lse: '🇬🇧',
};

/** Falls back to a neutral flag rather than an empty cell. */
export function exchangeFlag(exchange: string | null | undefined): string {
  if (!exchange) {
    return '🏳️';
  }
  return EXCHANGE_FLAGS[exchange.trim().toLowerCase()] ?? '🏳️';
}

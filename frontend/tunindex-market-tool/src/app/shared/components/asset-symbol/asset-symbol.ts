import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * The visual identity for an instrument or indicator.
 *
 * <p>Drawn here rather than loaded, for two reasons. The artifact and the app
 * both block outside images, so a logo URL would render as a broken box; and a
 * grid of unlabelled numbers is what these panels were before - the symbol is
 * what lets a reader find gold or the policy rate without reading every row.
 *
 * <p>Each mark is either the instrument's own glyph where one exists and is
 * unambiguous (a currency sign), or a simple drawn shape where it does not.
 * Crypto tickers get their letter rather than an invented logo: an approximate
 * Bitcoin mark looks like a mistake, while "B" on a brand colour reads as a
 * deliberate label.
 */
@Component({
  selector: 'app-asset-symbol',
  imports: [],
  templateUrl: './asset-symbol.html',
  styleUrl: './asset-symbol.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssetSymbol {
  /** Backend key: GOLD, SILVER, BTC, USD_TND, TMM, inflation, … */
  readonly assetKey = input.required<string>();
  readonly size = input<'sm' | 'md'>('md');

  /** Which mark to draw. Everything unrecognised falls back to initials. */
  protected readonly kind = computed<
    'gold' | 'silver' | 'crypto' | 'currency' | 'rate' | 'inflation' | 'growth' | 'generic'
  >(() => {
    const key = (this.assetKey() ?? '').toUpperCase();
    if (key === 'GOLD') return 'gold';
    if (key === 'SILVER') return 'silver';
    if (['BTC', 'ETH', 'SOL', 'BNB', 'XRP', 'ADA'].includes(key)) return 'crypto';
    if (key.includes('TND') || key.includes('USD') || key.includes('EUR')) return 'currency';
    if (key.includes('TMM') || key.includes('RATE') || key.includes('POLICY')) return 'rate';
    if (key.includes('INFLATION') || key.includes('CPI')) return 'inflation';
    if (key.includes('GDP') || key.includes('GROWTH')) return 'growth';
    return 'generic';
  });

  /** The currency sign for an FX pair - the quote side is always the dinar. */
  protected readonly currencyGlyph = computed(() => {
    const key = (this.assetKey() ?? '').toUpperCase();
    if (key.startsWith('USD')) return '$';
    if (key.startsWith('EUR')) return '€';
    if (key.startsWith('GBP')) return '£';
    if (key.startsWith('JPY')) return '¥';
    return 'DT';
  });

  /** Single letter for a crypto, which is how these are commonly marked. */
  protected readonly cryptoLetter = computed(() => (this.assetKey() ?? '?').charAt(0).toUpperCase());

  /** Brand-ish hue per crypto, so three tickers are not three grey circles. */
  protected readonly cryptoTone = computed(() => {
    switch ((this.assetKey() ?? '').toUpperCase()) {
      case 'BTC': return '#f7931a';
      case 'ETH': return '#6b7bd6';
      case 'SOL': return '#12a594';
      default: return '#7a7a88';
    }
  });

  protected readonly initials = computed(() =>
    (this.assetKey() ?? '?').replace(/[^A-Za-z]/g, '').slice(0, 2).toUpperCase() || '?',
  );
}

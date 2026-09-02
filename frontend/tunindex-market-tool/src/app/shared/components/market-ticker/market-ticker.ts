import { ChangeDetectionStrategy, Component } from '@angular/core';

interface TickerItem {
  symbol: string;
  price: string;
  changePct: number;
}

// Illustrative snapshot for the decorative ticker strip — not live data.
const TICKER_ITEMS: TickerItem[] = [
  { symbol: 'BIAT', price: '128.40', changePct: 0.86 },
  { symbol: 'SFBT', price: '19.75', changePct: -0.42 },
  { symbol: 'BH', price: '10.12', changePct: 1.24 },
  { symbol: 'PGH', price: '5.63', changePct: 0.31 },
  { symbol: 'STB', price: '3.98', changePct: -0.75 },
  { symbol: 'UIB', price: '22.05', changePct: 0.58 },
  { symbol: 'ATB', price: '4.41', changePct: -0.23 },
  { symbol: 'BNA', price: '11.87', changePct: 0.14 },
  { symbol: 'UBCI', price: '18.30', changePct: 1.02 },
];

/**
 * Decorative, continuously scrolling market strip — pure CSS animation
 * (no JS timer, so there's nothing to leak or clean up). The content is
 * duplicated once so the marquee loops seamlessly.
 */
@Component({
  selector: 'app-market-ticker',
  imports: [],
  templateUrl: './market-ticker.html',
  styleUrl: './market-ticker.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'market-ticker-host' },
})
export class MarketTicker {
  protected readonly items = TICKER_ITEMS;
}

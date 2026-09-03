import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MarketNewsItem } from '../../../core/models/market.model';
import { Market } from '../../../core/services/market';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';

/**
 * Market-wide headlines from the exchange feed — the whole BVMT rather
 * than one symbol. Each item carries the rule-based sentiment tag and, when
 * the source filed the story against a stock, that stock's day move.
 */
@Component({
  selector: 'app-market-news',
  imports: [DatePipe, DecimalPipe, SkeletonBlock],
  templateUrl: './market-news.html',
  styleUrl: './market-news.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketNews {
  private readonly market = inject(Market);

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly items = signal<MarketNewsItem[]>([]);

  protected readonly positiveCount = computed(
    () => this.items().filter((i) => i.sentiment === 'POSITIVE').length,
  );
  protected readonly negativeCount = computed(
    () => this.items().filter((i) => i.sentiment === 'NEGATIVE').length,
  );

  constructor() {
    this.market.getNews(12).subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  protected sentimentClass(sentiment: string | null): string {
    if (sentiment === 'POSITIVE') return 'positive';
    if (sentiment === 'NEGATIVE') return 'negative';
    return 'neutral';
  }
}

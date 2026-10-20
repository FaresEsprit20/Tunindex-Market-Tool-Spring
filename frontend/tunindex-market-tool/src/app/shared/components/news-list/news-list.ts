import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { Stock } from '../../../core/services/stock';
import { NewsImpact } from '../../../core/models/news-impact.model';
import { EmptyState } from '../empty-state/empty-state';
import { SkeletonBlock } from '../skeleton-block/skeleton-block';

/**
 * Real news headlines for one symbol, scraped from ilboursa.com, each
 * paired with a transparent rule-based sentiment tag and the real price
 * move that followed (see Stock.getNewsImpact / NewsSentimentClassifier +
 * NewsImpactServiceImpl on the collector — a fixed keyword list and real
 * PriceHistory rows, not a model or a prediction). Shared between
 * stock-detail and analysis so both pages render the exact same data
 * through one implementation.
 */
@Component({
  selector: 'app-news-list',
  imports: [DatePipe, DecimalPipe, EmptyState, SkeletonBlock],
  templateUrl: './news-list.html',
  styleUrl: './news-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NewsList {
  readonly symbol = input.required<string>();
  readonly limit = input(10);

  private readonly stockService = inject(Stock);

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly items = signal<NewsImpact[]>([]);

  constructor() {
    effect(() => {
      const symbol = this.symbol();
      const limit = this.limit();
      if (!symbol) {
        this.items.set([]);
        this.loading.set(false);
        return;
      }

      this.loading.set(true);
      this.error.set(false);
      this.stockService.getNewsImpact(symbol, limit).subscribe({
        next: (res) => {
          this.items.set(res);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set(true);
        },
      });
    });
  }

  protected sentimentClass(sentiment: string): string {
    if (sentiment === 'POSITIVE') return 'positive';
    if (sentiment === 'NEGATIVE') return 'negative';
    return 'neutral';
  }
}

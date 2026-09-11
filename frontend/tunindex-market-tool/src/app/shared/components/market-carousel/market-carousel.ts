import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Market } from '../../../core/services/market';
import { MarketMover } from '../../../core/models/market-breadth.model';
import { MarketNewsItem } from '../../../core/models/market.model';

/** One slide: either the day's movers or a headline. */
type Slide =
  | { kind: 'movers'; title: string; tone: 'up' | 'down' | 'active'; movers: MarketMover[] }
  | { kind: 'news'; item: MarketNewsItem };

/**
 * The day's market in a rotating band.
 *
 * <p>The dashboard already held this information, spread across three panels
 * a reader had to scroll to. The point of a carousel here is not decoration:
 * it puts the two things that change every day - what moved and what was
 * written about it - at the top, in the space one panel would have taken.
 *
 * <p>Rotation pauses on hover and on keyboard focus, and the dots are real
 * buttons. An auto-advancing band that steals a headline out from under
 * someone mid-sentence is the reason carousels have the reputation they do.
 */
@Component({
  selector: 'app-market-carousel',
  imports: [DecimalPipe, RouterLink],
  templateUrl: './market-carousel.html',
  styleUrl: './market-carousel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketCarousel implements OnDestroy {
  private readonly market = inject(Market);
  private readonly destroyRef = inject(DestroyRef);

  /** Long enough to read a headline without hurrying. */
  private static readonly ROTATE_MS = 7000;

  protected readonly loading = signal(true);
  protected readonly index = signal(0);
  protected readonly paused = signal(false);

  private readonly gainers = signal<MarketMover[]>([]);
  private readonly losers = signal<MarketMover[]>([]);
  private readonly active = signal<MarketMover[]>([]);
  private readonly news = signal<MarketNewsItem[]>([]);

  private timer?: ReturnType<typeof setInterval>;

  protected readonly slides = computed<Slide[]>(() => {
    const out: Slide[] = [];
    if (this.gainers().length > 0) {
      out.push({ kind: 'movers', title: "Today's biggest gainers", tone: 'up', movers: this.gainers().slice(0, 4) });
    }
    if (this.losers().length > 0) {
      out.push({ kind: 'movers', title: "Today's biggest fallers", tone: 'down', movers: this.losers().slice(0, 4) });
    }
    if (this.active().length > 0) {
      out.push({ kind: 'movers', title: 'Most actively traded', tone: 'active', movers: this.active().slice(0, 4) });
    }
    // Interleaved after the movers rather than bunched: a reader who stays
    // for one rotation sees both kinds of thing.
    this.news().slice(0, 4).forEach((item) => out.push({ kind: 'news', item }));
    return out;
  });

  protected readonly current = computed(() => this.slides()[this.index()] ?? null);

  constructor() {
    forkJoin({
      breadth: this.market.getBreadth().pipe(catchError(() => of(null))),
      news: this.market.getNews(6).pipe(catchError(() => of([] as MarketNewsItem[]))),
    }).subscribe(({ breadth, news }) => {
      this.gainers.set(breadth?.topGainers ?? []);
      this.losers.set(breadth?.topLosers ?? []);
      this.active.set(breadth?.mostActive ?? []);
      this.news.set(news);
      this.loading.set(false);
      this.start();
    });

    this.destroyRef.onDestroy(() => this.stop());
  }

  private start(): void {
    this.stop();
    if (this.slides().length < 2) {
      return;
    }
    this.timer = setInterval(() => {
      if (!this.paused()) {
        this.next();
      }
    }, MarketCarousel.ROTATE_MS);
  }

  private stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }

  protected next(): void {
    const count = this.slides().length;
    if (count > 0) {
      this.index.set((this.index() + 1) % count);
    }
  }

  protected previous(): void {
    const count = this.slides().length;
    if (count > 0) {
      this.index.set((this.index() - 1 + count) % count);
    }
  }

  protected goTo(i: number): void {
    this.index.set(i);
  }

  /** Hovering or focusing stops the rotation until the reader leaves. */
  protected pause(): void {
    this.paused.set(true);
  }

  protected resume(): void {
    this.paused.set(false);
  }

  protected sentimentClass(sentiment: string | null): string {
    if (sentiment === 'POSITIVE') return 'positive';
    if (sentiment === 'NEGATIVE') return 'negative';
    return 'neutral';
  }

  ngOnDestroy(): void {
    this.stop();
  }
}

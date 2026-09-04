import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Notification } from '../../../core/services/notification';
import { Portfolio as PortfolioService } from '../../../core/services/portfolio';
import { Stock } from '../../../core/services/stock';
import { PortfolioPosition, PortfolioSummary, PortfolioTransaction } from '../../../core/models/portfolio.model';
import { StockDto } from '../../../core/models/stock.model';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { StatTile } from '../../../shared/components/stat-tile/stat-tile';
import { Sparkline } from '../../../shared/components/sparkline/sparkline';
import { OpportunityScore, VERDICT_LABELS, Verdict } from '../../../core/models/opportunity.model';
import { PortfolioAnalyticsPanel } from '../portfolio-analytics/portfolio-analytics';

/**
 * IBKR-style paper trading simulator scoped to Tunisian (BVMT) stocks.
 * Every figure here — quotes, fills, P&L — comes from the real backend
 * (see core/services/portfolio.ts); nothing is simulated client-side.
 */
@Component({
  selector: 'app-portfolio',
  imports: [DecimalPipe, DatePipe, EmptyState, SkeletonBlock, StatTile, Sparkline, PortfolioAnalyticsPanel],
  templateUrl: './portfolio.html',
  styleUrl: './portfolio.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Portfolio {
  private readonly portfolioService = inject(PortfolioService);
  private readonly stockService = inject(Stock);
  private readonly notification = inject(Notification);

  protected readonly verdictLabels = VERDICT_LABELS;

  protected readonly loading = signal(true);
  protected readonly summary = signal<PortfolioSummary | null>(null);
  protected readonly transactions = signal<PortfolioTransaction[]>([]);

  protected readonly tradeSymbol = signal('');
  protected readonly tradeQuantity = signal('');
  protected readonly quote = signal<StockDto | null>(null);
  protected readonly quoteLoading = signal(false);
  protected readonly quoteError = signal(false);
  protected readonly submitting = signal(false);
  protected readonly resetting = signal(false);
  /** Score for the symbol in the ticket — so you see what you're buying. */
  protected readonly quoteScore = signal<OpportunityScore | null>(null);
  protected readonly quoteSpark = signal<number[]>([]);
  /** symbol -> closes, for the sparkline on each held position. */
  protected readonly positionSparks = signal<Record<string, number[]>>({});

  protected readonly parsedQuantity = computed(() => {
    const raw = Number(this.tradeQuantity());
    return Number.isFinite(raw) && raw > 0 ? raw : null;
  });

  protected readonly estimatedTotal = computed(() => {
    const q = this.quote();
    const qty = this.parsedQuantity();
    if (!q || q.lastPrice === null || qty === null) return null;
    return q.lastPrice * qty;
  });

  protected readonly cashBalance = computed(() => this.summary()?.cashBalance ?? 0);

  /** Cash left after this order — negative means it can't be afforded. */
  protected readonly cashAfterBuy = computed(() => {
    const total = this.estimatedTotal();
    return total === null ? null : this.cashBalance() - total;
  });

  protected readonly canAfford = computed(() => {
    const after = this.cashAfterBuy();
    return after === null ? true : after >= 0;
  });

  /** Most whole shares the current cash balance covers. */
  protected readonly maxAffordable = computed(() => {
    const price = this.quote()?.lastPrice;
    if (!price || price <= 0) return 0;
    return Math.floor(this.cashBalance() / price);
  });

  /** Shares already held of the symbol in the ticket, if any. */
  protected readonly existingPosition = computed(() => {
    const symbol = this.tradeSymbol().trim().toUpperCase();
    if (!symbol) return null;
    return this.summary()?.positions.find((p) => p.symbol === symbol) ?? null;
  });

  protected readonly dayChangePct = computed(() => {
    const q = this.quote();
    if (!q || q.lastPrice === null || q.prevClose === null || q.prevClose === 0) return null;
    return ((q.lastPrice - q.prevClose) / q.prevClose) * 100;
  });

  /** Each position's share of total market value, for the allocation bar. */
  protected readonly allocation = computed(() => {
    const positions = this.summary()?.positions ?? [];
    const total = positions.reduce((sum, p) => sum + p.marketValue, 0);
    if (total <= 0) return [];
    return positions
      .map((p) => ({ symbol: p.symbol, pct: (p.marketValue / total) * 100, value: p.marketValue }))
      .sort((a, b) => b.pct - a.pct);
  });

  protected fillQuantity(fraction: number): void {
    const max = this.maxAffordable();
    const qty = Math.max(1, Math.floor(max * fraction));
    this.tradeQuantity.set(max > 0 ? String(qty) : '');
  }

  protected sparkFor(symbol: string): number[] {
    return this.positionSparks()[symbol] ?? [];
  }

  protected verdictClass(verdict: Verdict): string {
    switch (verdict) {
      case 'STRONG_BUY':
        return 'strong-buy';
      case 'BUY':
        return 'buy';
      case 'WATCH':
        return 'watch';
      default:
        return 'hold';
    }
  }

  constructor() {
    this.loadPortfolio();
    this.loadTransactions();
  }

  private loadPortfolio(): void {
    this.loading.set(true);
    this.portfolioService.getPortfolio().subscribe({
      next: (res) => {
        this.summary.set(res);
        this.loading.set(false);
        const symbols = res.positions.map((p) => p.symbol);
        if (symbols.length > 0) {
          this.stockService.getSparklines(symbols, 30).subscribe({
            next: (series) => this.positionSparks.set(series),
            error: () => this.positionSparks.set({}),
          });
        }
      },
      error: () => this.loading.set(false),
    });
  }

  private loadTransactions(): void {
    this.portfolioService.getTransactions().subscribe({
      next: (res) => this.transactions.set(res),
      error: () => {},
    });
  }

  protected onSymbolInput(value: string): void {
    this.tradeSymbol.set(value.toUpperCase());
    this.quote.set(null);
    this.quoteScore.set(null);
    this.quoteSpark.set([]);
    this.quoteError.set(false);
  }

  /**
   * Day profit in dinars for one position. The API gives a day percentage
   * per position and a total in currency, but not the per-row cash figure —
   * and "-1.30%" does not tell you whether that is three dinars or three
   * hundred, which is the thing a holder actually reacts to.
   */
  protected dayPnl(position: PortfolioPosition): number | null {
    if (position.dayChangeValue !== null && position.dayChangeValue !== undefined) {
      return position.dayChangeValue;
    }
    if (position.prevClose === null || position.currentPrice === null) {
      return null;
    }
    return (position.currentPrice - position.prevClose) * position.quantity;
  }

  /** What was paid for the whole position, against which P&L is measured. */
  protected costBasis(position: PortfolioPosition): number {
    return position.avgCostBasis * position.quantity;
  }

  /**
   * Share of the invested book. Computed against the positions total rather
   * than total portfolio value: cash is not an exposure, and including it
   * would make every weight shrink as the account sits idle.
   */
  protected weightPct(position: PortfolioPosition, positions: PortfolioPosition[]): number | null {
    const invested = positions.reduce((sum, item) => sum + item.marketValue, 0);
    if (invested === 0) {
      return null;
    }
    return (position.marketValue / invested) * 100;
  }

  protected totalCostBasis(positions: PortfolioPosition[]): number {
    return positions.reduce((sum, item) => sum + this.costBasis(item), 0);
  }

  protected totalDayPnl(positions: PortfolioPosition[]): number {
    return positions.reduce((sum, item) => sum + (this.dayPnl(item) ?? 0), 0);
  }

  protected selectSymbolForTrade(symbol: string): void {
    this.tradeSymbol.set(symbol);
    this.lookupQuote();
  }

  protected lookupQuote(): void {
    const symbol = this.tradeSymbol().trim();
    if (!symbol) return;
    this.quoteLoading.set(true);
    this.quoteError.set(false);
    this.quote.set(null);
    this.quoteScore.set(null);
    this.quoteSpark.set([]);
    this.stockService.findBySymbol(symbol).subscribe({
      next: (res) => {
        this.quote.set(res);
        this.quoteLoading.set(false);
        // Both are context, not gates: a failure leaves the panel out
        // rather than blocking the trade.
        this.stockService.getScore(res.symbol).subscribe({
          next: (score) => this.quoteScore.set(score),
          error: () => this.quoteScore.set(null),
        });
        this.stockService.getSparklines([res.symbol], 30).subscribe({
          next: (series) => this.quoteSpark.set(series[res.symbol] ?? []),
          error: () => this.quoteSpark.set([]),
        });
      },
      error: () => {
        this.quoteLoading.set(false);
        this.quoteError.set(true);
      },
    });
  }

  protected buy(): void {
    this.executeTrade('buy');
  }

  protected sell(): void {
    this.executeTrade('sell');
  }

  private executeTrade(side: 'buy' | 'sell'): void {
    const symbol = this.tradeSymbol().trim();
    const quantity = this.parsedQuantity();
    if (!symbol || quantity === null || this.submitting()) return;

    this.submitting.set(true);
    const request$ =
      side === 'buy' ? this.portfolioService.buy(symbol, quantity) : this.portfolioService.sell(symbol, quantity);

    request$.subscribe({
      next: (tx) => {
        this.submitting.set(false);
        this.notification.show(
          side === 'buy' ? 'Order filled' : 'Position sold',
          `${side === 'buy' ? 'Bought' : 'Sold'} ${tx.quantity} ${tx.symbol} @ ${tx.price.toFixed(2)} TND.`,
          'success',
        );
        this.tradeQuantity.set('');
        this.quote.set(null);
    this.quoteScore.set(null);
    this.quoteSpark.set([]);
        this.tradeSymbol.set('');
        this.loadPortfolio();
        this.loadTransactions();
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.notification.show('Order rejected', this.extractError(err), 'error');
      },
    });
  }

  protected resetAccount(): void {
    const confirmed = window.confirm(
      'Reset the simulator? This clears all positions and trade history and restores your starting 20,000 TND cash balance.',
    );
    if (!confirmed) return;

    this.resetting.set(true);
    this.portfolioService.reset().subscribe({
      next: (res) => {
        this.resetting.set(false);
        this.summary.set(res);
        this.transactions.set([]);
        this.notification.show('Simulator reset', 'Your paper trading account has been reset.', 'success');
      },
      error: () => {
        this.resetting.set(false);
        this.notification.show('Reset failed', 'Please try again.', 'error');
      },
    });
  }

  private extractError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const errors = err.error?.errors as string[] | undefined;
      const message = err.error?.message as string | undefined;
      return errors?.join(' ') ?? message ?? 'Something went wrong. Please try again.';
    }
    return 'Something went wrong. Please try again.';
  }
}

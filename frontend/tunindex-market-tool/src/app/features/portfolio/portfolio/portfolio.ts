import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Notification } from '../../../core/services/notification';
import { Portfolio as PortfolioService } from '../../../core/services/portfolio';
import { Stock } from '../../../core/services/stock';
import { PortfolioSummary, PortfolioTransaction } from '../../../core/models/portfolio.model';
import { StockDto } from '../../../core/models/stock.model';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { StatTile } from '../../../shared/components/stat-tile/stat-tile';

/**
 * IBKR-style paper trading simulator scoped to Tunisian (BVMT) stocks.
 * Every figure here — quotes, fills, P&L — comes from the real backend
 * (see core/services/portfolio.ts); nothing is simulated client-side.
 */
@Component({
  selector: 'app-portfolio',
  imports: [DecimalPipe, DatePipe, EmptyState, SkeletonBlock, StatTile],
  templateUrl: './portfolio.html',
  styleUrl: './portfolio.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Portfolio {
  private readonly portfolioService = inject(PortfolioService);
  private readonly stockService = inject(Stock);
  private readonly notification = inject(Notification);

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
    this.quoteError.set(false);
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
    this.stockService.findBySymbol(symbol).subscribe({
      next: (res) => {
        this.quote.set(res);
        this.quoteLoading.set(false);
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
      'Reset the simulator? This clears all positions and trade history and restores your starting 100,000 TND cash balance.',
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

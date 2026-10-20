import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { MarketSession, SessionState } from '../../../core/models/market.model';
import { Market } from '../../../core/services/market';

/** How often to re-ask the server where the session stands. */
const REFRESH_INTERVAL_MS = 60_000;

/**
 * The BVMT trading session: what's happening now and how long until the
 * next transition. The countdown ticks locally off the server's own
 * seconds-remaining figure, so it stays right even if the browser clock is
 * off, and re-syncs on every refresh.
 */
@Component({
  selector: 'app-market-status',
  imports: [],
  templateUrl: './market-status.html',
  styleUrl: './market-status.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketStatus {
  private readonly market = inject(Market);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading = signal(true);
  protected readonly session = signal<MarketSession | null>(null);
  private readonly remaining = signal(0);

  protected readonly isOpen = computed(() => {
    const state = this.session()?.state;
    return state === 'OPEN' || state === 'PRE_OPEN' || state === 'PRE_CLOSE';
  });

  protected readonly countdown = computed(() => {
    const total = this.remaining();
    if (total <= 0) return '—';
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const seconds = total % 60;
    const pad = (n: number) => String(n).padStart(2, '0');
    return hours > 0 ? `${hours}h ${pad(minutes)}m` : `${pad(minutes)}:${pad(seconds)}`;
  });

  protected readonly tunisClock = computed(() => {
    const iso = this.session()?.tunisTime;
    if (!iso) return '—';
    // The server sends Tunis local time with no offset; rendering the raw
    // time-of-day avoids the browser re-interpreting it in its own zone.
    return iso.slice(11, 16);
  });

  constructor() {
    this.load();
    const refreshId = setInterval(() => this.load(), REFRESH_INTERVAL_MS);
    const tickId = setInterval(() => this.remaining.update((v) => (v > 0 ? v - 1 : 0)), 1000);
    this.destroyRef.onDestroy(() => {
      clearInterval(refreshId);
      clearInterval(tickId);
    });
  }

  private load(): void {
    this.market.getSession().subscribe({
      next: (session) => {
        this.session.set(session);
        this.remaining.set(session.secondsUntilTransition);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected stateClass(state: SessionState | undefined): string {
    switch (state) {
      case 'OPEN':
        return 'open';
      case 'PRE_OPEN':
      case 'PRE_CLOSE':
        return 'auction';
      case 'WEEKEND':
        return 'weekend';
      default:
        return 'closed';
    }
  }
}

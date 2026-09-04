import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { MarketSession, SessionState } from '../../../core/models/market.model';
import { Market } from '../../../core/services/market';

/**
 * Where the BVMT trading day stands, in the masthead.
 *
 * <p>The countdown runs off a local tick seeded once from the server's
 * `secondsUntilTransition`. Two reasons it is not driven by the wall clock:
 * the browser's clock can be wrong or in another timezone, and the exchange's
 * schedule is defined in Africa/Tunis. Counting down a server-supplied
 * duration sidesteps both.
 *
 * <p>It re-fetches on transition rather than polling on a timer — the only
 * moment the state can change is the one we are already counting down to.
 */
@Component({
  selector: 'app-session-clock',
  imports: [],
  templateUrl: './session-clock.html',
  styleUrl: './session-clock.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionClock implements OnDestroy {
  private readonly market = inject(Market);
  private timer: ReturnType<typeof setInterval> | null = null;

  protected readonly session = signal<MarketSession | null>(null);
  protected readonly remaining = signal(0);

  constructor() {
    this.load();
    this.timer = setInterval(() => this.tick(), 1000);
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  /** Only OPEN is a "live" state; PRE_OPEN and PRE_CLOSE are auction phases. */
  protected readonly isOpen = computed(() => this.session()?.state === 'OPEN');

  protected readonly toneClass = computed(() => {
    const state = this.session()?.state;
    switch (state) {
      case 'OPEN':
        return 'open';
      case 'PRE_OPEN':
      case 'PRE_CLOSE':
        return 'auction';
      default:
        return 'closed';
    }
  });

  /**
   * Days are shown when the gap is longer than a day — "in 61:12:04" is
   * unreadable, and over a weekend that is exactly the gap on offer.
   */
  protected readonly countdown = computed(() => {
    const seconds = this.remaining();
    if (seconds <= 0) {
      return null;
    }
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (days > 0) {
      return `${days}d ${hours}h`;
    }
    if (hours > 0) {
      return `${hours}h ${this.pad(minutes)}m`;
    }
    return `${minutes}:${this.pad(secs)}`;
  });

  private pad(value: number): string {
    return value.toString().padStart(2, '0');
  }

  private load(): void {
    this.market.getSession().subscribe({
      next: (session) => {
        this.session.set(session);
        this.remaining.set(Math.max(session.secondsUntilTransition, 0));
      },
      // Silent: the masthead simply shows nothing rather than an error badge
      // sitting permanently next to the brand.
      error: () => this.session.set(null),
    });
  }

  private tick(): void {
    const next = this.remaining() - 1;
    if (next <= 0 && this.session()) {
      // The transition we were counting down to has arrived; ask the server
      // what the new state is instead of guessing the next phase locally.
      this.remaining.set(0);
      this.load();
      return;
    }
    this.remaining.set(next);
  }

  protected stateLabel(state: SessionState): string {
    switch (state) {
      case 'OPEN':
        return 'Open';
      case 'PRE_OPEN':
        return 'Pre-open';
      case 'PRE_CLOSE':
        return 'Pre-close';
      case 'WEEKEND':
        return 'Weekend';
      default:
        return 'Closed';
    }
  }
}

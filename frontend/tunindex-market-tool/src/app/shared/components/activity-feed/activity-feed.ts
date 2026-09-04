import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppNotification } from '../../../core/models/alert.model';
import { Alerts } from '../../../core/services/alerts';

/**
 * What the platform has actually told this user, most recent first.
 *
 * <p>Alerts, filled orders and watchlist moves share one stream because that
 * is how they arrive — the distinction matters when you are configuring a
 * rule, not when you are catching up on what happened. Category tabs are
 * there for when it does.
 *
 * <p>This exists because the notifications were effectively invisible: they
 * were published, persisted and pushed over SSE, but the only place to see
 * one was a bell badge that cleared the moment you looked at it.
 *
 * <p>Reads the {@link Alerts} service's signal rather than fetching its own
 * copy, so the bell count and this list cannot disagree, and a notification
 * arriving over the stream shows up here without a refresh.
 */
@Component({
  selector: 'app-activity-feed',
  imports: [DatePipe, RouterLink],
  templateUrl: './activity-feed.html',
  styleUrl: './activity-feed.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivityFeed {
  readonly limit = input(20);

  private readonly alerts = inject(Alerts);

  protected readonly filter = signal<'ALL' | 'ALERT' | 'TRADE' | 'WATCHLIST'>('ALL');

  constructor() {
    // Errors are swallowed on purpose: the bell already surfaces a failure,
    // and a second error banner for the same cause is noise.
    this.alerts.refreshNotifications(this.limit()).subscribe({ error: () => undefined });
  }

  protected readonly items = computed<AppNotification[]>(() => this.alerts.notifications());

  protected readonly visible = computed(() => {
    const active = this.filter();
    return active === 'ALL'
      ? this.items()
      : this.items().filter((item) => item.category === active);
  });

  /**
   * Only categories actually present get a tab. A filter that can only ever
   * produce an empty list is worse than no filter.
   */
  protected readonly categories = computed(() => {
    const present = new Set(this.items().map((item) => item.category));
    return (['ALERT', 'TRADE', 'WATCHLIST'] as const).filter((key) => present.has(key));
  });

  protected countFor(category: string): number {
    return this.items().filter((item) => item.category === category).length;
  }

  protected label(category: string): string {
    switch (category) {
      case 'TRADE':
        return 'Orders';
      case 'WATCHLIST':
        return 'Watchlist';
      case 'ALERT':
        return 'Alerts';
      default:
        return category;
    }
  }

  protected setFilter(value: 'ALL' | 'ALERT' | 'TRADE' | 'WATCHLIST'): void {
    this.filter.set(value);
  }
}

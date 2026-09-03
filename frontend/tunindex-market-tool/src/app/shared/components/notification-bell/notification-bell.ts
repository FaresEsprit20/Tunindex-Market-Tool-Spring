import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppNotification } from '../../../core/models/alert.model';
import { Alerts } from '../../../core/services/alerts';

/**
 * The notification centre in the navbar. Reads from the Alerts service,
 * which holds one shared list fed by both the initial fetch and the live
 * server-sent stream — so the badge updates the moment a rule fires,
 * without this component polling.
 */
@Component({
  selector: 'app-notification-bell',
  imports: [DatePipe],
  templateUrl: './notification-bell.html',
  styleUrl: './notification-bell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationBell {
  private readonly alerts = inject(Alerts);
  private readonly router = inject(Router);
  private readonly elementRef = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly notifications = this.alerts.notifications;
  protected readonly unreadCount = this.alerts.unreadCount;
  protected readonly open = signal(false);

  constructor() {
    this.alerts.connect();

    document.addEventListener('click', this.onDocumentClick, { capture: true });
    this.destroyRef.onDestroy(() => {
      document.removeEventListener('click', this.onDocumentClick, { capture: true });
    });
  }

  private readonly onDocumentClick = (event: MouseEvent): void => {
    if (!this.elementRef.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  };

  protected toggle(): void {
    this.open.update((v) => !v);
  }

  protected markAllRead(): void {
    this.alerts.markAllRead().subscribe({ error: () => undefined });
  }

  protected onNotificationClick(notification: AppNotification): void {
    if (!notification.read) {
      this.alerts.markRead(notification.id).subscribe({ error: () => undefined });
    }
    this.open.set(false);
    if (notification.symbol) {
      void this.router.navigate(['/app/stocks', notification.symbol]);
    }
  }

  protected openAlerts(): void {
    this.open.set(false);
    void this.router.navigate(['/app/alerts']);
  }

  protected toneClass(tone: string): string {
    if (tone === 'POSITIVE') return 'positive';
    if (tone === 'NEGATIVE') return 'negative';
    return 'neutral';
  }
}

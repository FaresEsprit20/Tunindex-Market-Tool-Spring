import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { Card } from '../../../shared/components/card/card';
import { Notification } from '../../../core/services/notification';

@Component({
  selector: 'app-account-lock',
  imports: [RouterLink, Card],
  templateUrl: './account-lock.html',
  styleUrl: './account-lock.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountLock {
  private readonly notification = inject(Notification);

  protected readonly requesting = signal(false);
  protected readonly requested = signal(false);

  // Mock — pending backend integration (see readme.md).
  protected requestUnlock(): void {
    if (this.requesting() || this.requested()) {
      return;
    }
    this.requesting.set(true);

    of(undefined)
      .pipe(delay(700))
      .subscribe(() => {
        this.requesting.set(false);
        this.requested.set(true);
        this.notification.show('Request sent', 'Support will follow up by email shortly.', 'success');
      });
  }
}

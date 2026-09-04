import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { NAV_ITEMS } from '../../../core/constants/nav-items';
import { Auth } from '../../../core/services/auth';
import { Notification } from '../../../core/services/notification';

/**
 * Primary navigation as a horizontal bar under the brand, the way finance
 * sites are actually laid out.
 *
 * <p>A left app-sidebar reads as internal tooling; a masthead with sections
 * running across it reads as a market site, and it hands the full width of
 * the window back to the data. Same NAV_ITEMS as before, so the command
 * palette and this bar can never disagree about what exists.
 */
@Component({
  selector: 'app-primary-nav',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './primary-nav.html',
  styleUrl: './primary-nav.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrimaryNav {
  private readonly auth = inject(Auth);
  private readonly notification = inject(Notification);
  private readonly router = inject(Router);

  protected readonly navItems = NAV_ITEMS;
  protected readonly currentUser = this.auth.currentUser;

  protected logout(): void {
    this.auth.logout().subscribe({
      next: () => {
        this.notification.show('Signed out', "You've been signed out.", 'success');
        void this.router.navigateByUrl('/auth/login');
      },
      error: () => this.notification.show('Sign out failed', 'Please try again.', 'error'),
    });
  }
}

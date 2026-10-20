import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Auth } from '../../../core/services/auth';
import { Notification } from '../../../core/services/notification';
import { NAV_ITEMS } from '../../../core/constants/nav-items';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Sidebar {
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

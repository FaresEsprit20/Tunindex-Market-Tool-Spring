import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Auth } from '../../../core/services/auth';
import { Notification } from '../../../core/services/notification';

interface NavItem {
  label: string;
  route: string;
  icon: string; // inline SVG path data, drawn in the template
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', route: '/app/dashboard', icon: 'M3 13h4v8H3zM10 3h4v18h-4zM17 8h4v13h-4z' },
  { label: 'Stocks', route: '/app/stocks', icon: 'M3 3v18h18M7 14l4-4 3 3 5-6' },
  { label: 'Watchlist', route: '/app/watchlist', icon: 'M12 4.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 17.8 6.8 20.6l1-5.8-4.3-4.1 5.9-.9z' },
  { label: 'Portfolio', route: '/app/portfolio', icon: 'M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM8 5V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v1M3 12h18' },
  { label: 'Analysis', route: '/app/analysis', icon: 'M4 19h16M7 15l3-4 3 2 4-6' },
  { label: 'Data Pipeline', route: '/app/pipeline', icon: 'M12 2v6M12 16v6M4.9 4.9l4.2 4.2M14.9 14.9l4.2 4.2M2 12h6M16 12h6M4.9 19.1l4.2-4.2M14.9 9.1l4.2-4.2' },
];

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

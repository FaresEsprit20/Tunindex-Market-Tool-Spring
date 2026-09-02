import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Theme } from '../../../core/services/theme';
import { PulseDot } from '../pulse-dot/pulse-dot';

@Component({
  selector: 'app-navbar',
  imports: [PulseDot],
  templateUrl: './navbar.html',
  styleUrl: './navbar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Navbar {
  protected readonly theme = inject(Theme);
  private readonly router = inject(Router);

  protected readonly searchQuery = signal('');

  protected onSearchSubmit(): void {
    const query = this.searchQuery().trim();
    if (!query) {
      return;
    }
    void this.router.navigate(['/app/stocks'], { queryParams: { q: query } });
  }
}

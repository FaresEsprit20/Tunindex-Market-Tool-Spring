import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Watchlist } from '../../../core/services/watchlist';

@Component({
  selector: 'app-watchlist-star',
  imports: [],
  templateUrl: './watchlist-star.html',
  styleUrl: './watchlist-star.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WatchlistStar {
  private readonly watchlist = inject(Watchlist);

  readonly symbol = input.required<string>();

  protected isWatched(): boolean {
    return this.watchlist.isWatched(this.symbol());
  }

  protected toggle(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.watchlist.toggle(this.symbol());
  }
}

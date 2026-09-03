import { Injectable, effect, signal } from '@angular/core';

export type DensityMode = 'comfortable' | 'compact' | 'terminal';

const STORAGE_KEY = 'tunindex-density';

export const DENSITY_OPTIONS: { value: DensityMode; label: string; hint: string }[] = [
  { value: 'comfortable', label: 'Comfortable', hint: 'Roomier rows, easier to scan while learning' },
  { value: 'compact', label: 'Compact', hint: 'The default balance' },
  { value: 'terminal', label: 'Terminal', hint: 'Maximum rows on screen' },
];

/**
 * How tightly the interface packs information.
 *
 * <p>IBKR ships one density and it is merciless — the main reason people
 * bounce off it. The spacing tokens make three levels nearly free, so a
 * newcomer can start readable and tighten as they get fluent, rather than
 * being handed a terminal on day one.
 *
 * <p>Applied as `data-density` on the root element; see the token blocks in
 * styles.scss.
 */
@Injectable({ providedIn: 'root' })
export class Density {
  private readonly _mode = signal<DensityMode>(this.readInitial());
  readonly mode = this._mode.asReadonly();

  constructor() {
    effect(() => {
      const mode = this._mode();
      document.documentElement.setAttribute('data-density', mode);
      try {
        localStorage.setItem(STORAGE_KEY, mode);
      } catch {
        // Private browsing: the setting just won't persist.
      }
    });
  }

  set(mode: DensityMode): void {
    this._mode.set(mode);
  }

  private readInitial(): DensityMode {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'comfortable' || stored === 'compact' || stored === 'terminal') {
        return stored;
      }
    } catch {
      // ignore
    }
    return 'compact';
  }
}

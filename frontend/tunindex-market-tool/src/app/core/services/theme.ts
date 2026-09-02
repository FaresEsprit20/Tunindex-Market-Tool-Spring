import { Injectable, effect, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'tunindex-theme';

@Injectable({ providedIn: 'root' })
export class Theme {
  private readonly _mode = signal<ThemeMode>(this.readInitial());
  readonly mode = this._mode.asReadonly();

  constructor() {
    effect(() => {
      const mode = this._mode();
      document.documentElement.setAttribute('data-theme', mode);
      try {
        localStorage.setItem(STORAGE_KEY, mode);
      } catch {
        // localStorage unavailable (private browsing, etc.) — theme just won't persist.
      }
    });
  }

  toggle(): void {
    this._mode.update((mode) => (mode === 'light' ? 'dark' : 'light'));
  }

  set(mode: ThemeMode): void {
    this._mode.set(mode);
  }

  private readInitial(): ThemeMode {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'light' || stored === 'dark') {
        return stored;
      }
    } catch {
      // ignore — fall through to system preference.
    }

    if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }

    return 'light';
  }
}

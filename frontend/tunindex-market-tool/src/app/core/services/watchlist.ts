import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'tunindex-watchlist';

/**
 * There's no backend endpoint for this yet, so it's local to the browser —
 * genuinely persisted (survives reloads) via localStorage, just not synced
 * across devices.
 */
@Injectable({ providedIn: 'root' })
export class Watchlist {
  readonly symbols = signal<string[]>(this.loadFromStorage());

  isWatched(symbol: string): boolean {
    return this.symbols().includes(symbol);
  }

  toggle(symbol: string): void {
    const current = this.symbols();
    const next = current.includes(symbol) ? current.filter((s) => s !== symbol) : [...current, symbol];
    this.symbols.set(next);
    this.persist(next);
  }

  private loadFromStorage(): string[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private persist(symbols: string[]): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(symbols));
    } catch {
      // Storage unavailable (private browsing, quota) — watchlist just won't persist this session.
    }
  }
}

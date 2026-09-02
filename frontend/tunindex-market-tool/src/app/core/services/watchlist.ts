import { HttpClient } from '@angular/common/http';
import { Injectable, effect, inject, signal } from '@angular/core';
import { API_BASE_URL } from '../config/api.config';
import { Auth } from './auth';

/**
 * Backed by the real per-user watchlist endpoints (GET/POST/DELETE
 * .../watchlist) — nothing here is local-only. `symbols` is a client-side
 * cache of the server's list, kept in sync by reacting to Auth.isAuthenticated
 * rather than being wired into every login/logout call site: it fetches once
 * auth state actually flips true (never on the pre-login page, where no
 * session exists yet), and clears itself the moment it flips false. This
 * also means a re-login as a different user in the same tab picks up the
 * new user's list correctly, with no separate hook needed.
 */
@Injectable({ providedIn: 'root' })
export class Watchlist {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(Auth);

  readonly symbols = signal<string[]>([]);

  constructor() {
    effect(() => {
      if (this.auth.isAuthenticated()) {
        this.fetch();
      } else {
        this.symbols.set([]);
      }
    });
  }

  private fetch(): void {
    this.http.get<string[]>(`${API_BASE_URL}/watchlist`).subscribe({
      next: (symbols) => this.symbols.set(symbols),
      error: () => this.symbols.set([]),
    });
  }

  isWatched(symbol: string): boolean {
    return this.symbols().includes(symbol);
  }

  toggle(symbol: string): void {
    const previous = this.symbols();
    const alreadyWatched = previous.includes(symbol);

    // Optimistic: flip immediately so the star responds instantly, then
    // reconcile with the server — roll back only if the request fails.
    this.symbols.set(alreadyWatched ? previous.filter((s) => s !== symbol) : [...previous, symbol]);

    const request$ = alreadyWatched
      ? this.http.delete<void>(`${API_BASE_URL}/watchlist/${encodeURIComponent(symbol)}`)
      : this.http.post<void>(`${API_BASE_URL}/watchlist/${encodeURIComponent(symbol)}`, {});

    request$.subscribe({
      error: () => this.symbols.set(previous),
    });
  }
}

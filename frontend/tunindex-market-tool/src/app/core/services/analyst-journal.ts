import { Injectable, signal } from '@angular/core';
import { AnalystCall } from '../models/analyst-call.model';

const STORAGE_KEY = 'tradify.analyst.calls.v1';

/** Enough history to be meaningful without growing without bound. */
const MAX_CALLS = 60;

/**
 * What the analyst has actually told you, and when.
 *
 * <p>This is the difference between a tip and an analyst. Anyone can say "buy
 * BHL"; being able to say "I said buy BHL at 7.60 on 12 September, here is
 * what it did since" is the thing that makes the next call worth listening
 * to — including, and especially, when the answer is "it fell". A record that
 * only surfaced the winners would be worth less than no record at all, so the
 * reporting on top of this never filters by outcome.
 *
 * <p>Kept in localStorage, which is a deliberate limitation rather than an
 * oversight: it is this browser's record, it does not follow the user to
 * another device, and clearing site data erases it. The copy built on it is
 * written to stay true under those conditions — it claims only that a call
 * was made and what the price was, both of which this file can prove. Moving
 * it server-side would make it portable and auditable, and is the natural
 * next step; nothing in the shape of this API would have to change.
 */
@Injectable({ providedIn: 'root' })
export class AnalystJournal {
  /** Newest first. */
  readonly calls = signal<AnalystCall[]>(this.read());

  /**
   * Records a call, unless the same one is already open.
   *
   * <p>The guard matters: the briefing runs on every page load, and without
   * it a week of visiting the dashboard would look like a week of separate
   * recommendations. One call per symbol until it stops being the call.
   */
  record(call: AnalystCall): void {
    const existing = this.calls();
    if (existing.some((c) => c.symbol === call.symbol)) return;

    const next = [call, ...existing].slice(0, MAX_CALLS);
    this.calls.set(next);
    this.write(next);
  }

  /** Drops a symbol from the record — used when a call is no longer live. */
  close(symbol: string): void {
    const next = this.calls().filter((c) => c.symbol !== symbol);
    if (next.length === this.calls().length) return;
    this.calls.set(next);
    this.write(next);
  }

  clear(): void {
    this.calls.set([]);
    this.write([]);
  }

  private read(): AnalystCall[] {
    // Storage throws in private mode and returns garbage if another version
    // of the app wrote it; neither should take the briefing down with it.
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return [];
      const parsed: unknown = JSON.parse(raw);
      if (!Array.isArray(parsed)) return [];
      return parsed.filter(
        (c): c is AnalystCall =>
          !!c &&
          typeof (c as AnalystCall).symbol === 'string' &&
          typeof (c as AnalystCall).price === 'number' &&
          typeof (c as AnalystCall).madeOn === 'string',
      );
    } catch {
      return [];
    }
  }

  private write(calls: AnalystCall[]): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(calls));
    } catch {
      // A full or unavailable store costs us the history, not the page.
    }
  }
}

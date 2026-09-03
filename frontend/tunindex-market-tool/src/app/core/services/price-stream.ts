import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { API_BASE_URL } from '../config/api.config';

export interface PriceTick {
  symbol: string;
  price: number;
  prevClose: number | null;
  changePct: number | null;
  direction: 'up' | 'down';
}

/** How long a cell stays flashed after a tick. */
const FLASH_MS = 1500;
const RECONNECT_MS = 15_000;

/**
 * Live price changes pushed from the server.
 *
 * <p>Two separate signals on purpose: {@link prices} is durable state the
 * grid reads to render current values, while {@link flashes} is transient
 * and self-clearing, so a cell lights up on the change and then settles.
 * Merging them would either make the flash permanent or make the price
 * vanish when the flash expired.
 */
@Injectable({ providedIn: 'root' })
export class PriceStream {
  private readonly destroyRef = inject(DestroyRef);

  /** symbol -> latest pushed tick. */
  private readonly _prices = signal<Record<string, PriceTick>>({});
  readonly prices = this._prices.asReadonly();

  /** symbol -> direction, present only while the flash is running. */
  private readonly _flashes = signal<Record<string, 'up' | 'down'>>({});
  readonly flashes = this._flashes.asReadonly();

  readonly connected = signal(false);

  private source: EventSource | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly flashTimers = new Map<string, ReturnType<typeof setTimeout>>();

  constructor() {
    this.destroyRef.onDestroy(() => this.disconnect());
  }

  connect(): void {
    if (this.source) {
      return;
    }
    try {
      const source = new EventSource(`${API_BASE_URL}/prices/stream`, { withCredentials: true });
      this.source = source;

      source.addEventListener('connected', () => this.connected.set(true));

      source.addEventListener('prices', (event) => {
        let ticks: PriceTick[];
        try {
          ticks = JSON.parse((event as MessageEvent).data) as PriceTick[];
        } catch {
          return;
        }

        this._prices.update((current) => {
          const next = { ...current };
          for (const tick of ticks) {
            next[tick.symbol] = tick;
          }
          return next;
        });

        this._flashes.update((current) => {
          const next = { ...current };
          for (const tick of ticks) {
            next[tick.symbol] = tick.direction;
          }
          return next;
        });

        // Each symbol clears on its own timer, so a later tick on one stock
        // doesn't cut short the flash on another.
        for (const tick of ticks) {
          clearTimeout(this.flashTimers.get(tick.symbol));
          this.flashTimers.set(
            tick.symbol,
            setTimeout(() => {
              this._flashes.update((current) => {
                const next = { ...current };
                delete next[tick.symbol];
                return next;
              });
              this.flashTimers.delete(tick.symbol);
            }, FLASH_MS),
          );
        }
      });

      source.onerror = () => {
        this.connected.set(false);
        this.disconnect();
        this.reconnectTimer = setTimeout(() => this.connect(), RECONNECT_MS);
      };
    } catch {
      this.source = null;
    }
  }

  disconnect(): void {
    this.source?.close();
    this.source = null;
    this.connected.set(false);
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    for (const timer of this.flashTimers.values()) {
      clearTimeout(timer);
    }
    this.flashTimers.clear();
  }
}

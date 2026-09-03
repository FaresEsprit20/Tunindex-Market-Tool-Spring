import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { AlertRule, AlertTypeOption, AppNotification, CreateAlertRule } from '../models/alert.model';

/** Reconnect delay after the browser drops the notification stream. */
const STREAM_RETRY_MS = 10_000;

/**
 * Alert rules plus the notification centre they feed.
 *
 * <p>Notifications arrive two ways and both land in the same signal: an
 * initial fetch on connect, and a server-sent stream for anything that
 * fires while the app is open. Because every notification is also persisted
 * server-side, a dropped stream costs nothing — the next fetch has it.
 */
@Injectable({ providedIn: 'root' })
export class Alerts {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _notifications = signal<AppNotification[]>([]);
  readonly notifications = this._notifications.asReadonly();
  readonly unreadCount = computed(() => this._notifications().filter((n) => !n.read).length);

  private eventSource: EventSource | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.disconnect());
  }

  // ── rules ────────────────────────────────────────────────────────────────

  listRules(): Observable<AlertRule[]> {
    return this.http.get<AlertRule[]>(`${API_BASE_URL}/alerts`);
  }

  listTypes(): Observable<AlertTypeOption[]> {
    return this.http.get<AlertTypeOption[]>(`${API_BASE_URL}/alerts/types`);
  }

  createRule(request: CreateAlertRule): Observable<AlertRule> {
    return this.http.post<AlertRule>(`${API_BASE_URL}/alerts`, request);
  }

  toggleRule(id: number): Observable<AlertRule> {
    return this.http.put<AlertRule>(`${API_BASE_URL}/alerts/${id}/toggle`, {});
  }

  deleteRule(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/alerts/${id}`);
  }

  // ── notifications ────────────────────────────────────────────────────────

  refreshNotifications(limit = 30): Observable<AppNotification[]> {
    return this.http
      .get<AppNotification[]>(`${API_BASE_URL}/notifications`, { params: { limit } })
      .pipe(tap((items) => this._notifications.set(items)));
  }

  markRead(id: number): Observable<void> {
    // Optimistic: the bell should respond to the click, not to the round trip.
    this._notifications.update((items) =>
      items.map((item) => (item.id === id ? { ...item, read: true } : item)),
    );
    return this.http.put<void>(`${API_BASE_URL}/notifications/${id}/read`, {});
  }

  markAllRead(): Observable<{ updated: number }> {
    this._notifications.update((items) => items.map((item) => ({ ...item, read: true })));
    return this.http.put<{ updated: number }>(`${API_BASE_URL}/notifications/read-all`, {});
  }

  /**
   * Opens the live stream. Auth rides on the accessToken cookie, which the
   * browser attaches to the EventSource request the same as any other — so
   * withCredentials is what makes this authenticate at all.
   */
  connect(): void {
    if (this.eventSource) {
      return;
    }

    this.refreshNotifications().subscribe({ error: () => undefined });

    try {
      const source = new EventSource(`${API_BASE_URL}/notifications/stream`, { withCredentials: true });
      this.eventSource = source;

      source.addEventListener('notification', (event) => {
        try {
          const incoming = JSON.parse((event as MessageEvent).data) as AppNotification;
          this._notifications.update((items) =>
            items.some((item) => item.id === incoming.id) ? items : [incoming, ...items],
          );
        } catch {
          // A malformed frame shouldn't tear down the stream.
        }
      });

      source.onerror = () => {
        // EventSource retries on its own, but only while the connection is
        // recoverable; closing and re-opening covers the rest.
        this.disconnect();
        this.retryTimer = setTimeout(() => this.connect(), STREAM_RETRY_MS);
      };
    } catch {
      this.eventSource = null;
    }
  }

  disconnect(): void {
    this.eventSource?.close();
    this.eventSource = null;
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }
}

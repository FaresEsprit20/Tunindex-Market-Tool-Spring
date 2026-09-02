import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { PipelineSnapshot } from '../models/pipeline.model';

interface StartResponse {
  started: boolean;
  totalStocks?: number;
  message?: string;
}

/**
 * Live pipeline status arrives over Server-Sent Events, not a polled HTTP
 * call — the backend pushes a new snapshot every time a worker's state
 * actually changes (plus a once-a-second heartbeat), so this mirrors that
 * push model with a signal rather than re-fetching on an interval.
 */
@Injectable({ providedIn: 'root' })
export class Pipeline {
  private readonly http = inject(HttpClient);

  readonly snapshot = signal<PipelineSnapshot | null>(null);
  readonly connected = signal(false);

  private eventSource: EventSource | null = null;
  private subscriberCount = 0;

  start(): Observable<StartResponse> {
    return this.http.post<StartResponse>(`${API_BASE_URL}/pipeline/start`, {});
  }

  /** Ref-counted so multiple components mounting/unmounting share one connection. */
  connect(): void {
    this.subscriberCount++;
    if (this.eventSource) {
      return;
    }

    const source = new EventSource(`${API_BASE_URL}/pipeline/status`, { withCredentials: true });
    this.eventSource = source;

    source.onopen = () => this.connected.set(true);
    source.onmessage = (event) => {
      try {
        this.snapshot.set(JSON.parse(event.data));
      } catch {
        // Ignore malformed frames rather than tearing down the stream.
      }
    };
    source.onerror = () => this.connected.set(false);
  }

  disconnect(): void {
    this.subscriberCount = Math.max(0, this.subscriberCount - 1);
    if (this.subscriberCount === 0 && this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
      this.connected.set(false);
    }
  }
}

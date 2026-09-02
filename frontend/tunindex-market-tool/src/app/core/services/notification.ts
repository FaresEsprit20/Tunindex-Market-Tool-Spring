import { Injectable, signal } from '@angular/core';

export type ToastVariant = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  title: string;
  message: string;
  variant: ToastVariant;
}

const AUTO_DISMISS_MS = 5000;

/**
 * Ticker/toast notifications — new lines translate up and fade in on
 * arrival (see .toast-slide-in in the shared ToastContainer component).
 */
@Injectable({ providedIn: 'root' })
export class Notification {
  private readonly _toasts = signal<Toast[]>([]);
  readonly toasts = this._toasts.asReadonly();

  private nextId = 0;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  show(title: string, message: string, variant: ToastVariant = 'info'): void {
    const id = this.nextId++;
    this._toasts.update((toasts) => [...toasts, { id, title, message, variant }]);

    const timer = setTimeout(() => this.dismiss(id), AUTO_DISMISS_MS);
    this.timers.set(id, timer);
  }

  dismiss(id: number): void {
    const timer = this.timers.get(id);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
    this._toasts.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }
}

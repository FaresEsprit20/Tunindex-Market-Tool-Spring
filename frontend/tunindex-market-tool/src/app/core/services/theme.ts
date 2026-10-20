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

  /**
   * Flips the theme. When the browser supports the View Transitions API and
   * the caller passes the toggle button's viewport position, the new theme
   * reveals via an expanding circle from that point instead of an instant
   * swap — startViewTransition snapshots old/new DOM states and we animate
   * a clip-path over the new one, purely in CSS (see styles.scss). Falls
   * back to a plain, instant flip for unsupported browsers or reduced-motion.
   */
  toggle(origin?: { x: number; y: number }): void {
    const next = this._mode() === 'light' ? 'dark' : 'light';
    const supportsViewTransition = typeof document.startViewTransition === 'function';
    const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

    if (!supportsViewTransition || reducedMotion || !origin) {
      this._mode.set(next);
      return;
    }

    const root = document.documentElement;
    const radius = Math.hypot(
      Math.max(origin.x, window.innerWidth - origin.x),
      Math.max(origin.y, window.innerHeight - origin.y),
    );
    root.style.setProperty('--theme-reveal-x', `${origin.x}px`);
    root.style.setProperty('--theme-reveal-y', `${origin.y}px`);
    root.style.setProperty('--theme-reveal-radius', `${radius}px`);
    // Distinguishes this transition from a router navigation's — both use
    // the browser's single default ::view-transition-*(root) pseudo pair,
    // so this class is how styles.scss picks the circular-reveal keyframes
    // instead of the route fade for this specific transition.
    root.classList.add('theme-transition');

    const transition = document.startViewTransition(() => {
      this._mode.set(next);
    });
    transition.finished.finally(() => root.classList.remove('theme-transition'));
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

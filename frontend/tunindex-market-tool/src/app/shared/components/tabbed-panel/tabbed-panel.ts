import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChildren,
  effect,
  input,
  signal,
} from '@angular/core';
import { Tab } from './tab';

/**
 * A panel holding several views behind small tabs.
 *
 * <p>The point is screen real estate: in a fixed-viewport workspace a panel
 * cannot grow, so stacking a chart under a score breakdown means scrolling
 * inside an already-small box. Tabs let one region carry four views at full
 * height instead of four views at a quarter each.
 *
 * <p>The active tab persists per {@link storageKey}, so a workspace comes
 * back the way it was left — the same reasoning as the split ratio.
 */
@Component({
  selector: 'app-tabbed-panel',
  imports: [NgTemplateOutlet],
  template: `
    <div class="tab-strip" role="tablist">
      @for (tab of tabs(); track tab.appTab()) {
        <button
          type="button"
          role="tab"
          class="tab"
          [class.active]="tab.appTab() === activeLabel()"
          [attr.aria-selected]="tab.appTab() === activeLabel()"
          (click)="select(tab.appTab())"
          (keydown)="onKeydown($event)"
        >{{ tab.appTab() }}</button>
      }
      <span class="tab-fill"></span>
      <ng-content select="[tabActions]" />
    </div>

    <div class="tab-body" role="tabpanel">
      @if (activeTemplate(); as tpl) {
        <ng-container [ngTemplateOutlet]="tpl" />
      }
    </div>
  `,
  styleUrl: './tabbed-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TabbedPanel {
  readonly storageKey = input<string>('');

  protected readonly tabs = contentChildren(Tab);
  private readonly chosen = signal<string | null>(null);

  /** Falls back to the first tab when nothing is chosen or the choice is gone. */
  protected readonly activeLabel = computed(() => {
    const labels = this.tabs().map((tab) => tab.appTab());
    const chosen = this.chosen();
    return chosen && labels.includes(chosen) ? chosen : (labels[0] ?? null);
  });

  protected readonly activeTemplate = computed(() => {
    const active = this.activeLabel();
    return this.tabs().find((tab) => tab.appTab() === active)?.template ?? null;
  });

  private restored = false;

  constructor() {
    // An effect rather than constructor code: signal inputs are not bound
    // yet at construction, so storageKey() would read as '' and the stored
    // tab would never be found.
    effect(() => {
      const key = this.storageKey();
      if (this.restored || !key) {
        return;
      }
      this.restored = true;
      try {
        const stored = localStorage.getItem(`tunindex-tab-${key}`);
        if (stored) {
          this.chosen.set(stored);
        }
      } catch {
        // Private browsing — the tab choice just won't persist.
      }
    });
  }

  protected select(label: string): void {
    this.chosen.set(label);
    const key = this.storageKey();
    if (!key) return;
    try {
      localStorage.setItem(`tunindex-tab-${key}`, label);
    } catch {
      // ignore
    }
  }

  /** ←/→ move between tabs, the standard tablist interaction. */
  protected onKeydown(event: KeyboardEvent): void {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
      return;
    }
    event.preventDefault();
    const labels = this.tabs().map((tab) => tab.appTab());
    const current = labels.indexOf(this.activeLabel() ?? '');
    if (current === -1) return;
    const step = event.key === 'ArrowRight' ? 1 : -1;
    this.select(labels[(current + step + labels.length) % labels.length]);
  }
}

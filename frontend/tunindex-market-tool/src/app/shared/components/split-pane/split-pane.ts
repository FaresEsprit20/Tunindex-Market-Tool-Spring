import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';

/**
 * Two panes with a draggable divider between them.
 *
 * <p>The split is stored as a <em>ratio</em>, not a pixel width, so a
 * layout survives a window resize instead of leaving one pane stranded at
 * its old size. Ratios persist per {@link storageKey}, which is what makes
 * an arranged workspace still arranged tomorrow.
 *
 * <p>Uses pointer events rather than mouse events so a trackpad, a touch
 * screen and a pen all work from one code path, and captures the pointer so
 * a fast drag that leaves the handle doesn't drop the gesture.
 */
@Component({
  selector: 'app-split-pane',
  imports: [],
  template: `
    <div class="pane pane-a" [style.flex-basis.%]="ratioPct()">
      <ng-content select="[paneA]" />
    </div>

    <div
      class="divider"
      [class.dragging]="dragging()"
      role="separator"
      tabindex="0"
      [attr.aria-valuenow]="Math.round(ratioPct())"
      aria-valuemin="0"
      aria-valuemax="100"
      [attr.aria-label]="label()"
      (pointerdown)="onPointerDown($event)"
      (keydown)="onKeydown($event)"
      (dblclick)="reset()"
    >
      <span class="grip"></span>
    </div>

    <div class="pane pane-b">
      <ng-content select="[paneB]" />
    </div>
  `,
  styleUrl: './split-pane.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SplitPane {
  /** Persists the divider position; omit for a non-remembering split. */
  readonly storageKey = input<string>('');
  readonly initialRatio = input(0.6);
  /** Neither pane may shrink below this fraction of the container. */
  readonly minRatio = input(0.25);
  readonly label = input('Resize panels');

  protected readonly Math = Math;

  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  private readonly ratio = signal(this.initialRatio());
  protected readonly dragging = signal(false);
  protected readonly ratioPct = computed(() => this.ratio() * 100);

  /** Guards the restore so a later re-run can't undo the user's drag. */
  private restored = false;

  constructor() {
    // Deliberately an effect, not constructor code: signal inputs are not
    // bound yet when the constructor runs, so storageKey() would read as ''
    // and the stored ratio would never be found. The effect runs once the
    // inputs are available.
    effect(() => {
      const key = this.storageKey();
      const initial = this.initialRatio();
      if (this.restored) {
        return;
      }
      this.restored = true;
      const stored = key ? this.readStored() : null;
      this.ratio.set(stored ?? this.clamp(initial));
    });

    this.destroyRef.onDestroy(() => this.detach());
  }

  protected onPointerDown(event: PointerEvent): void {
    event.preventDefault();
    this.dragging.set(true);
    // Capture means the drag keeps tracking even when the pointer outruns
    // the 6px handle, which it always does.
    (event.target as HTMLElement).setPointerCapture(event.pointerId);
    window.addEventListener('pointermove', this.onPointerMove);
    window.addEventListener('pointerup', this.onPointerUp);
  }

  private readonly onPointerMove = (event: PointerEvent): void => {
    const rect = this.host.nativeElement.getBoundingClientRect();
    if (rect.width === 0) return;
    const raw = (event.clientX - rect.left) / rect.width;
    this.ratio.set(this.clamp(raw));
  };

  private readonly onPointerUp = (): void => {
    this.dragging.set(false);
    this.detach();
    this.persist();
  };

  /** Keyboard resizing, in 2% steps — a divider nobody can reach is not a control. */
  protected onKeydown(event: KeyboardEvent): void {
    const step = 0.02;
    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      this.ratio.set(this.clamp(this.ratio() - step));
      this.persist();
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.ratio.set(this.clamp(this.ratio() + step));
      this.persist();
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.reset();
    }
  }

  /** Double-click or Enter restores the default split. */
  protected reset(): void {
    this.ratio.set(this.initialRatio());
    this.persist();
  }

  private clamp(value: number): number {
    const min = this.minRatio();
    return Math.min(Math.max(value, min), 1 - min);
  }

  private detach(): void {
    window.removeEventListener('pointermove', this.onPointerMove);
    window.removeEventListener('pointerup', this.onPointerUp);
  }

  private persist(): void {
    const key = this.storageKey();
    if (!key) return;
    try {
      localStorage.setItem(`tunindex-split-${key}`, this.ratio().toFixed(4));
    } catch {
      // Private browsing — the layout just won't be remembered.
    }
  }

  private readStored(): number | null {
    const key = this.storageKey();
    if (!key) return null;
    try {
      const raw = localStorage.getItem(`tunindex-split-${key}`);
      if (!raw) return null;
      const parsed = Number(raw);
      return Number.isFinite(parsed) ? this.clamp(parsed) : null;
    } catch {
      return null;
    }
  }
}

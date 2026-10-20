import { ChangeDetectionStrategy, Component, ElementRef, input, signal, viewChild } from '@angular/core';

/**
 * The small "?" badge used next to jargon or non-obvious fields. Its tooltip
 * is rendered `position: fixed` with coordinates computed from the trigger's
 * own `getBoundingClientRect()` at open time — never `position: absolute` —
 * because every scrollable panel body in this app uses `overflow: auto`,
 * which silently clips an absolutely-positioned tooltip the moment it needs
 * to extend past its panel. Apply the same technique to any dropdown/picker
 * that must render outside a scrolling ancestor.
 */
@Component({
  selector: 'app-info-hint',
  imports: [],
  templateUrl: './info-hint.html',
  styleUrl: './info-hint.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'info-hint-host' },
})
export class InfoHint {
  readonly label = input<string>('More information');

  protected readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  protected readonly open = signal(false);
  protected readonly pos = signal({ top: 0, left: 0 });

  protected show(): void {
    const rect = this.trigger().nativeElement.getBoundingClientRect();
    this.pos.set({ top: rect.bottom + 8, left: rect.left });
    this.open.set(true);
  }

  protected hide(): void {
    this.open.set(false);
  }
}

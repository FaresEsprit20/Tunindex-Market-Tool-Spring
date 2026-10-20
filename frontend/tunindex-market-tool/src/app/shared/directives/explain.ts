import { Directive, ElementRef, inject, input, DestroyRef } from '@angular/core';

/**
 * Shows the arithmetic behind a derived number on hover or keyboard focus.
 *
 * <p>Every figure in this app traces to published inputs; this is what turns
 * that property into something a user can feel. A P/E stops being a number
 * you take on trust and becomes `13.31 = 160.60 ÷ 12.06`.
 *
 * <p>The popover is appended to <body> and positioned from the trigger's
 * viewport rect rather than nested in the DOM: the workspace layout puts
 * `overflow: auto` on nearly every container, and an absolutely-positioned
 * child would be clipped by the first one it met.
 */
@Directive({
  selector: '[appExplain]',
  host: {
    '(mouseenter)': 'show()',
    '(mouseleave)': 'hide()',
    '(focus)': 'show()',
    '(blur)': 'hide()',
    '(keydown.escape)': 'hide()',
    tabindex: '0',
    class: 'explainable',
  },
})
export class Explain {
  /** The formula line, e.g. "160.60 ÷ 12.06". Empty disables the popover. */
  readonly appExplain = input<string>('');
  /** Optional second line naming what the figure means. */
  readonly explainNote = input<string>('');

  private readonly host = inject(ElementRef<HTMLElement>);
  private popover: HTMLElement | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.hide());
  }

  protected show(): void {
    const formula = this.appExplain();
    if (!formula || this.popover) {
      return;
    }

    const pop = document.createElement('div');
    pop.className = 'explain-pop';
    pop.setAttribute('role', 'tooltip');

    const line = document.createElement('div');
    line.className = 'explain-formula';
    line.textContent = formula;
    pop.appendChild(line);

    const note = this.explainNote();
    if (note) {
      const noteEl = document.createElement('div');
      noteEl.className = 'explain-note';
      noteEl.textContent = note;
      pop.appendChild(noteEl);
    }

    document.body.appendChild(pop);
    this.popover = pop;

    const rect = this.host.nativeElement.getBoundingClientRect();
    const popRect = pop.getBoundingClientRect();

    // Prefer above the trigger; flip below when there isn't room, and clamp
    // horizontally so it never runs off either edge.
    const above = rect.top - popRect.height - 8;
    const top = above > 8 ? above : rect.bottom + 8;
    const left = Math.min(
      Math.max(8, rect.left + rect.width / 2 - popRect.width / 2),
      window.innerWidth - popRect.width - 8,
    );

    pop.style.top = `${Math.round(top)}px`;
    pop.style.left = `${Math.round(left)}px`;
    pop.classList.add('visible');
  }

  protected hide(): void {
    this.popover?.remove();
    this.popover = null;
  }
}

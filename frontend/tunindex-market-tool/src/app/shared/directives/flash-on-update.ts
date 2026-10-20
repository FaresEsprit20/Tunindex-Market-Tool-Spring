import { Directive, ElementRef, effect, inject, input } from '@angular/core';

/**
 * Single-shot background wash on an element whose bound value just changed
 * — never looping. Restarts the CSS animation on every change by
 * removing the class, forcing a reflow, then re-adding it.
 *
 * Usage: <span [appFlashOnUpdate]="portfolioValue()">{{ portfolioValue() }}</span>
 */
@Directive({
  selector: '[appFlashOnUpdate]',
})
export class FlashOnUpdate {
  readonly watch = input<unknown>(undefined, { alias: 'appFlashOnUpdate' });

  private readonly element = inject(ElementRef<HTMLElement>).nativeElement;
  private isFirstRun = true;

  constructor() {
    effect(() => {
      this.watch();

      if (this.isFirstRun) {
        this.isFirstRun = false;
        return;
      }

      this.element.classList.remove('flash-once');
      // Force a reflow so the browser registers the class removal before
      // it's re-added, otherwise the animation silently fails to restart.
      void this.element.offsetWidth;
      this.element.classList.add('flash-once');
    });
  }
}

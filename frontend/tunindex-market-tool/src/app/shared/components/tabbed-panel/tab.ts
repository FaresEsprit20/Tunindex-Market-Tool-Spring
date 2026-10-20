import { Directive, TemplateRef, inject, input } from '@angular/core';

/**
 * Marks one view inside a {@link TabbedPanel}.
 *
 * <p>A template, not a rendered block: the panel only instantiates the tab
 * that is actually showing, so an expensive view (a chart, a scored
 * breakdown) costs nothing until someone opens it.
 *
 * <pre>&lt;ng-template appTab="Chart"&gt;…&lt;/ng-template&gt;</pre>
 */
@Directive({ selector: '[appTab]' })
export class Tab {
  /** The tab's visible label, and its identity for persistence. */
  readonly appTab = input.required<string>();
  readonly template = inject(TemplateRef<unknown>);
}

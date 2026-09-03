import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * A country flag as inline SVG, keyed by ISO 3166 alpha-2.
 *
 * <p>Emoji flags are not an option here: Windows ships no glyphs for the
 * regional-indicator pairs and renders them as the bare letters ("TN"),
 * which is most of this app's audience. Drawing them costs a few hundred
 * bytes each and looks identical on every platform.
 */
@Component({
  selector: 'app-country-flag',
  imports: [],
  template: `
    <svg [attr.width]="width()" [attr.height]="height()" viewBox="0 0 24 16" [attr.aria-label]="label()" role="img">
      @switch (code().toUpperCase()) {
        @case ('TN') {
          <rect width="24" height="16" fill="#E70013" />
          <circle cx="12" cy="8" r="5" fill="#fff" />
          <!-- Crescent: a red disc overlapped by an offset white one. -->
          <circle cx="12" cy="8" r="3.3" fill="#E70013" />
          <circle cx="13.1" cy="8" r="2.6" fill="#fff" />
          <path d="M12.55 5.9 13.1 7.4l1.6.05-1.25 1 .45 1.53-1.35-.88-1.35.88.45-1.53-1.25-1 1.6-.05z" fill="#E70013" />
        }
        @case ('MA') {
          <rect width="24" height="16" fill="#C1272D" />
          <path d="M12 5.2 13.15 8.6 12 11.3l-1.15-2.7z" fill="none" stroke="#006233" stroke-width="0.8" />
          <path d="M8.9 7.5h6.2l-5 3.6 1.9-5.9 1.9 5.9z" fill="none" stroke="#006233" stroke-width="0.8" />
        }
        @case ('DZ') {
          <rect width="12" height="16" fill="#006233" />
          <rect x="12" width="12" height="16" fill="#fff" />
          <circle cx="12" cy="8" r="4" fill="#D21034" />
          <circle cx="13.3" cy="8" r="3.2" fill="#fff" />
          <path d="M13.6 6.2l.5 1.4 1.5.05-1.15.9.4 1.4-1.25-.8-1.25.8.4-1.4-1.15-.9 1.5-.05z" fill="#D21034" />
        }
        @case ('EG') {
          <rect width="24" height="5.33" fill="#CE1126" />
          <rect y="5.33" width="24" height="5.33" fill="#fff" />
          <rect y="10.66" width="24" height="5.34" fill="#000" />
          <path d="M12 6.4l.9 2.2h-1.8z" fill="#C09300" />
        }
        @case ('EU') {
          <rect width="24" height="16" fill="#003399" />
          <g fill="#FFCC00">
            <circle cx="12" cy="4.4" r="0.7" />
            <circle cx="12" cy="11.6" r="0.7" />
            <circle cx="8.4" cy="8" r="0.7" />
            <circle cx="15.6" cy="8" r="0.7" />
            <circle cx="9.5" cy="5.5" r="0.7" />
            <circle cx="14.5" cy="10.5" r="0.7" />
            <circle cx="9.5" cy="10.5" r="0.7" />
            <circle cx="14.5" cy="5.5" r="0.7" />
          </g>
        }
        @case ('US') {
          <rect width="24" height="16" fill="#fff" />
          <g fill="#B22234">
            <rect width="24" height="1.85" y="0" />
            <rect width="24" height="1.85" y="3.7" />
            <rect width="24" height="1.85" y="7.4" />
            <rect width="24" height="1.85" y="11.1" />
            <rect width="24" height="1.85" y="14.15" />
          </g>
          <rect width="10" height="8.6" fill="#3C3B6E" />
        }
        @case ('GB') {
          <rect width="24" height="16" fill="#012169" />
          <path d="M0 0l24 16M24 0L0 16" stroke="#fff" stroke-width="3" />
          <path d="M0 0l24 16M24 0L0 16" stroke="#C8102E" stroke-width="1.6" />
          <path d="M12 0v16M0 8h24" stroke="#fff" stroke-width="5" />
          <path d="M12 0v16M0 8h24" stroke="#C8102E" stroke-width="3" />
        }
        @default {
          <rect width="24" height="16" fill="var(--color-panel-alt)" stroke="var(--color-border)" stroke-width="1" />
          <text
            x="12"
            y="11.5"
            text-anchor="middle"
            font-size="8"
            font-family="var(--font-mono)"
            fill="var(--color-text-tertiary)"
          >{{ code().toUpperCase().slice(0, 2) }}</text>
        }
      }
    </svg>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        line-height: 0;
        flex-shrink: 0;
      }
      svg {
        display: block;
        border-radius: 1px;
        // A hairline keeps a white-edged flag from dissolving into a light
        // panel without implying a heavier border.
        box-shadow: 0 0 0 0.5px rgba(0, 0, 0, 0.16);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CountryFlag {
  readonly code = input.required<string>();
  readonly width = input(16);
  readonly title = input('');

  protected readonly height = computed(() => Math.round((this.width() / 24) * 16));
  protected readonly label = computed(() => this.title() || `${this.code().toUpperCase()} flag`);
}

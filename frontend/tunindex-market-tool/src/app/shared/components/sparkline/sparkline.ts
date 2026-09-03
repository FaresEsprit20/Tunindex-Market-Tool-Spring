import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const WIDTH = 68;
// Sized to sit inside a terminal row rather than set its height.
const HEIGHT = 18;
const PADDING = 1.5;

/**
 * A row-scale price trace: real closes, no axis, no labels.
 *
 * <p>Coloured by the net move over the window rather than by the last tick,
 * so the colour answers "how has this done lately" — the question a
 * sparkline in a table row is actually there to answer. A flat or
 * single-point series draws a mid-line rather than dividing by a zero range.
 */
@Component({
  selector: 'app-sparkline',
  imports: [],
  template: `
    @if (points().length > 1) {
      <svg
        [attr.viewBox]="'0 0 ' + width + ' ' + height"
        [attr.width]="width"
        [attr.height]="height"
        preserveAspectRatio="none"
        role="img"
        [attr.aria-label]="ariaLabel()"
      >
        <path [attr.d]="areaPath()" [attr.fill]="'url(#' + gradientId + ')'" />
        <path
          [attr.d]="linePath()"
          fill="none"
          [attr.stroke]="strokeColor()"
          stroke-width="1.4"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <circle [attr.cx]="lastX()" [attr.cy]="lastY()" r="1.8" [attr.fill]="strokeColor()" />
        <defs>
          <linearGradient [attr.id]="gradientId" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" [attr.stop-color]="strokeColor()" stop-opacity="0.22" />
            <stop offset="1" [attr.stop-color]="strokeColor()" stop-opacity="0" />
          </linearGradient>
        </defs>
      </svg>
    } @else {
      <span class="sparkline-empty">—</span>
    }
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        line-height: 0;
      }
      svg {
        display: block;
        overflow: visible;
      }
      .sparkline-empty {
        font-size: 12px;
        color: var(--color-text-tertiary);
        line-height: 1;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Sparkline {
  readonly points = input<number[]>([]);

  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;

  /** Gradients are referenced by id, so each instance needs its own. */
  protected readonly gradientId = `spark-${Math.random().toString(36).slice(2, 9)}`;

  private readonly scaled = computed(() => {
    const values = this.points();
    if (values.length < 2) return [];

    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min;
    const usableHeight = HEIGHT - PADDING * 2;
    const step = (WIDTH - PADDING * 2) / (values.length - 1);

    return values.map((value, index) => ({
      x: PADDING + index * step,
      // A flat series has no range to scale against; draw it down the middle.
      y: range === 0 ? HEIGHT / 2 : PADDING + usableHeight - ((value - min) / range) * usableHeight,
    }));
  });

  protected readonly linePath = computed(() =>
    this.scaled()
      .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(2)} ${point.y.toFixed(2)}`)
      .join(' '),
  );

  protected readonly areaPath = computed(() => {
    const points = this.scaled();
    if (points.length < 2) return '';
    const line = this.linePath();
    const lastX = points[points.length - 1].x.toFixed(2);
    const firstX = points[0].x.toFixed(2);
    return `${line} L${lastX} ${HEIGHT} L${firstX} ${HEIGHT} Z`;
  });

  protected readonly lastX = computed(() => {
    const points = this.scaled();
    return points.length ? points[points.length - 1].x : 0;
  });

  protected readonly lastY = computed(() => {
    const points = this.scaled();
    return points.length ? points[points.length - 1].y : 0;
  });

  protected readonly strokeColor = computed(() => {
    const values = this.points();
    if (values.length < 2) return 'var(--color-text-tertiary)';
    const net = values[values.length - 1] - values[0];
    if (net > 0) return 'var(--color-positive)';
    if (net < 0) return 'var(--color-negative)';
    return 'var(--color-text-tertiary)';
  });

  protected readonly ariaLabel = computed(() => {
    const values = this.points();
    if (values.length < 2) return 'No price history';
    const net = ((values[values.length - 1] - values[0]) / values[0]) * 100;
    return `Price trend, ${net >= 0 ? 'up' : 'down'} ${Math.abs(net).toFixed(1)}% over the window`;
  });
}

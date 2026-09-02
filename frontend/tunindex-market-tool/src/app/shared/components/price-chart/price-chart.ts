import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, signal } from '@angular/core';
import { PriceHistoryPoint } from '../../../core/models/price-history.model';

interface ChartPoint {
  x: number;
  y: number;
  date: string;
  close: number;
}

const VIEW_WIDTH = 600;
const VIEW_HEIGHT = 220;
const PADDING_Y = 12;

/**
 * Real line chart over real daily closes — see Stock.getHistory(), backed by
 * IlBoursaHistoryProvider's scraped OHLCV. Renders nothing (not a flat line,
 * not a placeholder) when there's no real series yet, rather than implying
 * data that doesn't exist.
 */
@Component({
  selector: 'app-price-chart',
  imports: [DecimalPipe],
  templateUrl: './price-chart.html',
  styleUrl: './price-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriceChart {
  private readonly elementRef = inject(ElementRef<HTMLElement>);

  readonly points = input.required<PriceHistoryPoint[]>();
  readonly loading = input(false);

  protected readonly viewWidth = VIEW_WIDTH;
  protected readonly viewHeight = VIEW_HEIGHT;

  protected readonly hoverIndex = signal<number | null>(null);

  private readonly validPoints = computed(() =>
    this.points().filter((p): p is PriceHistoryPoint & { close: number } => p.close !== null),
  );

  protected readonly hasData = computed(() => this.validPoints().length >= 2);

  private readonly priceRange = computed(() => {
    const closes = this.validPoints().map((p) => p.close);
    return { min: Math.min(...closes), max: Math.max(...closes) };
  });

  protected readonly chartPoints = computed<ChartPoint[]>(() => {
    const pts = this.validPoints();
    if (pts.length < 2) return [];
    const { min, max } = this.priceRange();
    const range = max - min || 1;
    const usableHeight = VIEW_HEIGHT - PADDING_Y * 2;

    return pts.map((p, i) => ({
      x: (i / (pts.length - 1)) * VIEW_WIDTH,
      y: PADDING_Y + usableHeight - ((p.close - min) / range) * usableHeight,
      date: p.tradeDate,
      close: p.close,
    }));
  });

  protected readonly linePath = computed(() => {
    const pts = this.chartPoints();
    if (pts.length === 0) return '';
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' ');
  });

  protected readonly areaPath = computed(() => {
    const pts = this.chartPoints();
    if (pts.length === 0) return '';
    const line = this.linePath();
    const last = pts[pts.length - 1];
    return `${line} L${last.x.toFixed(2)} ${VIEW_HEIGHT} L0 ${VIEW_HEIGHT} Z`;
  });

  protected readonly firstPoint = computed(() => this.chartPoints()[0] ?? null);
  protected readonly lastPoint = computed(() => {
    const pts = this.chartPoints();
    return pts[pts.length - 1] ?? null;
  });
  protected readonly priceRangeLabel = computed(() => {
    const { min, max } = this.priceRange();
    return { min, max };
  });

  protected readonly hoveredPoint = computed(() => {
    const idx = this.hoverIndex();
    if (idx === null) return null;
    return this.chartPoints()[idx] ?? null;
  });

  protected onMouseMove(event: MouseEvent): void {
    const pts = this.chartPoints();
    if (pts.length === 0) return;

    const svg = this.elementRef.nativeElement.querySelector('svg');
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const relativeX = ((event.clientX - rect.left) / rect.width) * VIEW_WIDTH;

    let closestIdx = 0;
    let closestDist = Infinity;
    pts.forEach((p, i) => {
      const dist = Math.abs(p.x - relativeX);
      if (dist < closestDist) {
        closestDist = dist;
        closestIdx = i;
      }
    });
    this.hoverIndex.set(closestIdx);
  }

  protected onMouseLeave(): void {
    this.hoverIndex.set(null);
  }

  protected formatDate(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}

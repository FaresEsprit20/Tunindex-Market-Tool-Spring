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
  viewChild,
} from '@angular/core';
import {
  CandlestickSeries,
  ColorType,
  IChartApi,
  ISeriesApi,
  LineSeries,
  UTCTimestamp,
  createChart,
} from 'lightweight-charts';
import { PriceHistoryPoint } from '../../../core/models/price-history.model';
import { Theme } from '../../../core/services/theme';

export type ChartStyle = 'candles' | 'line';

/**
 * Real OHLCV candlestick/line chart via TradingView's own lightweight-charts
 * library, over the same real daily history as PriceChart (see
 * Stock.getHistory / IlBoursaHistoryProvider). Colors are read from this
 * app's own design tokens at render time — including --color-positive /
 * --color-negative, which already match lightweight-charts' own default
 * up/down candle colors — so the chart matches the rest of the UI in both
 * light and dark mode rather than shipping a hardcoded palette.
 */
@Component({
  selector: 'app-candlestick-chart',
  imports: [],
  templateUrl: './candlestick-chart.html',
  styleUrl: './candlestick-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CandlestickChart {
  private readonly theme = inject(Theme);
  private readonly destroyRef = inject(DestroyRef);
  private readonly container = viewChild.required<ElementRef<HTMLDivElement>>('chartContainer');

  readonly points = input.required<PriceHistoryPoint[]>();
  readonly loading = input(false);

  protected readonly style = signal<ChartStyle>('candles');

  protected readonly hasData = computed(() => this.points().filter((p) => p.close !== null).length >= 2);

  private chart: IChartApi | null = null;
  private series: ISeriesApi<'Candlestick'> | ISeriesApi<'Line'> | null = null;
  private resizeObserver: ResizeObserver | null = null;

  constructor() {
    effect(() => {
      // Track every input this render depends on so the chart rebuilds
      // whenever any of them change — new data, a style toggle, or a
      // theme switch.
      const points = this.points();
      const style = this.style();
      this.theme.mode();

      if (!this.hasData()) {
        this.destroyChart();
        return;
      }

      queueMicrotask(() => this.render(points, style));
    });

    this.destroyRef.onDestroy(() => this.destroyChart());
  }

  protected setStyle(style: ChartStyle): void {
    this.style.set(style);
  }

  private render(points: PriceHistoryPoint[], style: ChartStyle): void {
    const el = this.container()?.nativeElement;
    if (!el) return;

    this.destroyChart();

    const tokens = this.readTokens(el);

    const chart = createChart(el, {
      width: el.clientWidth,
      height: 340,
      layout: {
        background: { type: ColorType.Solid, color: tokens.panel },
        textColor: tokens.textSecondary,
        fontFamily: tokens.fontMono,
      },
      grid: {
        vertLines: { color: tokens.border },
        horzLines: { color: tokens.border },
      },
      rightPriceScale: { borderColor: tokens.border },
      timeScale: { borderColor: tokens.border, timeVisible: false },
      crosshair: { mode: 0 },
    });

    const sorted = points
      .filter((p): p is PriceHistoryPoint & { close: number } => p.close !== null)
      .slice()
      .sort((a, b) => a.tradeDate.localeCompare(b.tradeDate));

    if (style === 'candles') {
      const series = chart.addSeries(CandlestickSeries, {
        upColor: tokens.positive,
        downColor: tokens.negative,
        borderUpColor: tokens.positive,
        borderDownColor: tokens.negative,
        wickUpColor: tokens.positive,
        wickDownColor: tokens.negative,
      });
      series.setData(
        sorted
          .filter((p) => p.open !== null && p.high !== null && p.low !== null)
          .map((p) => ({
            time: toUtcTimestamp(p.tradeDate),
            open: p.open!,
            high: p.high!,
            low: p.low!,
            close: p.close,
          })),
      );
      this.series = series;
    } else {
      const series = chart.addSeries(LineSeries, {
        color: tokens.brand,
        lineWidth: 2,
      });
      series.setData(sorted.map((p) => ({ time: toUtcTimestamp(p.tradeDate), value: p.close })));
      this.series = series;
    }

    chart.timeScale().fitContent();
    this.chart = chart;

    this.resizeObserver = new ResizeObserver(() => {
      if (this.chart && el.clientWidth > 0) {
        this.chart.applyOptions({ width: el.clientWidth });
      }
    });
    this.resizeObserver.observe(el);
  }

  private readTokens(el: HTMLElement) {
    const computed = getComputedStyle(el);
    const read = (name: string, fallback: string) => computed.getPropertyValue(name).trim() || fallback;
    return {
      panel: read('--color-panel', '#ffffff'),
      border: read('--color-border', '#e4e8ef'),
      textSecondary: read('--color-text-secondary', '#556080'),
      positive: read('--color-positive', '#089981'),
      negative: read('--color-negative', '#f23645'),
      brand: read('--color-brand', '#1656e8'),
      fontMono: read('--font-mono', 'monospace'),
    };
  }

  private destroyChart(): void {
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    this.chart?.remove();
    this.chart = null;
    this.series = null;
  }
}

function toUtcTimestamp(isoDate: string): UTCTimestamp {
  return (new Date(isoDate + 'T00:00:00Z').getTime() / 1000) as UTCTimestamp;
}

import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { RiskMetrics } from '../../../core/models/risk.model';
import { Risk } from '../../../core/services/risk';
import { RiskPanel } from './risk-panel';

function metrics(overrides: Partial<RiskMetrics> = {}): RiskMetrics {
  return {
    symbol: 'AB',
    observations: 247,
    periodStart: '2025-09-05',
    periodEnd: '2026-09-02',
    annualisedVolatilityPct: 20.05,
    downsideDeviationPct: 9.13,
    maxDrawdownPct: -9.85,
    maxDrawdownPeak: '2026-07-17',
    maxDrawdownTrough: '2026-07-28',
    periodReturnPct: 104.22,
    annualisedReturnPct: 106.33,
    beta: 1.1212,
    varianceExplained: 0.2075,
    sharpeRatio: 4.9051,
    sortinoRatio: 10.7718,
    riskFreeRatePct: 8,
    valueAtRisk95Pct: -1.12,
    conditionalVar95Pct: -2.06,
    bestDayPct: 5.56,
    worstDayPct: -3.06,
    positiveDaysPct: 44.53,
    methodology: ['Computed from 248 stored daily closes.', 'Beta uses an equal-weighted proxy.'],
    ...overrides,
  };
}

describe('RiskPanel', () => {
  let fixture: ComponentFixture<RiskPanel>;

  async function setup(service: Partial<Risk>, symbol = 'AB') {
    // Reset first: a couple of these tests configure the module twice to
    // compare two server responses, which TestBed otherwise rejects.
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [RiskPanel],
      providers: [provideZonelessChangeDetection(), { provide: Risk, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(RiskPanel);
    fixture.componentRef.setInput('symbol', symbol);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('renders the served figures without recomputing them', async () => {
    await setup({ getMetrics: () => of(metrics()) });
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('20.1%');
    expect(text).toContain('-9.9%');
    expect(text).toContain('1.12');
    expect(text).toContain('4.91');
  });

  it('dashes a figure the server declined to compute, rather than showing zero', async () => {
    await setup({ getMetrics: () => of(metrics({ beta: null, varianceExplained: null })) });
    const items = [...fixture.nativeElement.querySelectorAll('.risk-item')] as HTMLElement[];
    const beta = items.find((item) => item.querySelector('dt')?.textContent?.trim() === 'Beta');

    expect(beta?.querySelector('dd')?.textContent?.trim()).toBe('—');
    // No interpretation is offered for a figure we do not have.
    expect(beta?.querySelector('.risk-hint')).toBeNull();
  });

  it('explains an insufficient sample instead of rendering an empty grid', async () => {
    await setup({
      getMetrics: () => of(metrics({ annualisedVolatilityPct: null, observations: 4 })),
    });

    expect(fixture.nativeElement.querySelector('.risk-note')?.textContent).toContain('Not enough');
    expect(fixture.nativeElement.querySelector('.risk-grid')).toBeNull();
  });

  it('shows the server methodology verbatim on request', async () => {
    await setup({ getMetrics: () => of(metrics()) });
    expect(fixture.nativeElement.querySelector('.risk-methodology')).toBeNull();

    (fixture.nativeElement.querySelector('.risk-method-toggle') as HTMLElement).click();
    fixture.detectChanges();

    const lines = fixture.nativeElement.querySelectorAll('.risk-methodology li');
    expect(lines.length).toBe(2);
    expect(lines[0].textContent).toContain('248 stored daily closes');
  });

  it('bands volatility against BVMT norms, not a textbook scale', async () => {
    await setup({ getMetrics: () => of(metrics({ annualisedVolatilityPct: 12 })) });
    expect(fixture.nativeElement.querySelector('.risk-hero-value')?.classList).toContain('low');

    await setup({ getMetrics: () => of(metrics({ annualisedVolatilityPct: 45 })) });
    expect(fixture.nativeElement.querySelector('.risk-hero-value')?.classList).toContain('high');
  });

  it('reports a failed lookup rather than blanking silently', async () => {
    await setup({ getMetrics: () => throwError(() => new Error('down')) });

    expect(fixture.nativeElement.querySelector('.risk-note')?.textContent).toContain('unavailable');
  });
});

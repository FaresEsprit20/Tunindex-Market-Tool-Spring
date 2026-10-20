import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PortfolioAnalytics } from '../../../core/models/portfolio-analytics.model';
import { Portfolio } from '../../../core/services/portfolio';
import { PortfolioAnalyticsPanel } from './portfolio-analytics';

function analytics(overrides: Partial<PortfolioAnalytics> = {}): PortfolioAnalytics {
  return {
    positionCount: 3,
    totalMarketValue: 1000,
    cashBalance: 500,
    cashWeightPct: 33.33,
    concentrationHhi: 3800,
    concentrationLabel: 'CONCENTRATED',
    effectivePositions: 2.6,
    largestPositionPct: 50,
    largestPositionSymbol: 'AB',
    largestSectorPct: 70,
    largestSectorName: 'Banking',
    positionWeights: [
      { key: 'AB', label: 'Amen Bank', marketValue: 500, weightPct: 50, positions: 1 },
      { key: 'BIAT', label: 'BIAT', marketValue: 300, weightPct: 30, positions: 1 },
      { key: 'SFBT', label: 'SFBT', marketValue: 200, weightPct: 20, positions: 1 },
    ],
    sectorWeights: [
      { key: 'BANKING', label: 'Banking', marketValue: 800, weightPct: 80, positions: 2 },
      { key: 'CONSUMER_GOODS', label: 'Consumer Goods', marketValue: 200, weightPct: 20, positions: 1 },
    ],
    weightedBeta: 0.94,
    betaCoveragePct: 100,
    projectedAnnualIncome: 42,
    portfolioYieldPct: 4.2,
    incomeCoveragePct: 100,
    incomeByPosition: [],
    observations: ['AB is 50% of the book.', 'Banking accounts for 80%.'],
    ...overrides,
  };
}

describe('PortfolioAnalyticsPanel', () => {
  let fixture: ComponentFixture<PortfolioAnalyticsPanel>;

  async function setup(service: Partial<Portfolio>) {
    await TestBed.configureTestingModule({
      imports: [PortfolioAnalyticsPanel],
      providers: [provideZonelessChangeDetection(), { provide: Portfolio, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(PortfolioAnalyticsPanel);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('renders the served exposure figures', async () => {
    await setup({ getAnalytics: () => of(analytics()) });
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Concentrated');
    expect(text).toContain('50.0%');
    expect(text).toContain('Banking');
    expect(text).toContain('2.6');
  });

  it('opens on the sector breakdown and switches to holdings on demand', async () => {
    await setup({ getAnalytics: () => of(analytics()) });
    expect(fixture.nativeElement.querySelectorAll('.pa-weight-row').length).toBe(2);

    const holdingTab = [...fixture.nativeElement.querySelectorAll('.pa-tab')].find(
      (tab: HTMLElement) => tab.textContent?.trim() === 'By holding',
    ) as HTMLElement;
    holdingTab.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.pa-weight-row').length).toBe(3);
  });

  it('scales bars to the largest slice so small weights stay comparable', async () => {
    await setup({ getAnalytics: () => of(analytics()) });
    const fills = fixture.nativeElement.querySelectorAll('.pa-weight-fill') as NodeListOf<HTMLElement>;

    // Largest sector is 80%, so it fills the track; 20% renders at a quarter.
    expect(parseFloat(fills[0].style.width)).toBeCloseTo(100, 1);
    expect(parseFloat(fills[1].style.width)).toBeCloseTo(25, 1);
  });

  it('renders the server observations verbatim', async () => {
    await setup({ getAnalytics: () => of(analytics()) });
    const notes = fixture.nativeElement.querySelectorAll('.pa-observations li');

    expect(notes.length).toBe(2);
    expect(notes[0].textContent).toContain('AB is 50% of the book.');
  });

  it('dashes a weighted beta the server withheld for thin coverage', async () => {
    await setup({ getAnalytics: () => of(analytics({ weightedBeta: null, betaCoveragePct: 20 })) });
    const stats = [...fixture.nativeElement.querySelectorAll('.pa-stat')] as HTMLElement[];
    const beta = stats.find((stat) => stat.textContent?.includes('Weighted beta'));

    expect(beta?.querySelector('.pa-stat-value')?.textContent?.trim()).toBe('—');
  });

  it('invites a first trade rather than showing an empty chart on a bare account', async () => {
    await setup({
      getAnalytics: () => of(analytics({ positionCount: 0, positionWeights: [], sectorWeights: [] })),
    });

    expect(fixture.nativeElement.querySelector('.pa-note')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.pa-weights')).toBeNull();
  });

  it('reports a failed lookup', async () => {
    await setup({ getAnalytics: () => throwError(() => new Error('down')) });

    expect(fixture.nativeElement.querySelector('.pa-note')?.textContent).toContain('unavailable');
  });
});

import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MarketBreadth } from '../../../core/models/market-breadth.model';
import { Market } from '../../../core/services/market';
import { MarketPulse } from './market-pulse';

function breadth(overrides: Partial<MarketBreadth> = {}): MarketBreadth {
  return {
    advancing: 3,
    declining: 2,
    unchanged: 1,
    notPriced: 4,
    total: 10,
    averageChangePct: 1.25,
    totalVolume: 5000,
    topGainers: [
      { symbol: 'UP', name: 'Up SA', sector: 'BANKING', exchange: 'TN', lastPrice: 110, prevClose: 100, changePct: 10, volume: 5 },
    ],
    topLosers: [
      { symbol: 'DOWN', name: 'Down SA', sector: 'BANKING', exchange: 'TN', lastPrice: 90, prevClose: 100, changePct: -10, volume: 5 },
    ],
    mostActive: [],
    sectorPerformance: [
      { sector: 'BANKING', averageChangePct: 4, advancing: 2, declining: 1, priced: 3, total: 3 },
      { sector: 'REAL_ESTATE', averageChangePct: -2, advancing: 0, declining: 1, priced: 1, total: 1 },
      { sector: 'MATERIALS', averageChangePct: null, advancing: 0, declining: 0, priced: 0, total: 2 },
    ],
    asOf: '2026-09-03T15:30:00',
    ...overrides,
  };
}

describe('MarketPulse', () => {
  let fixture: ComponentFixture<MarketPulse>;

  async function setup(service: Partial<Market>) {
    await TestBed.configureTestingModule({
      imports: [MarketPulse],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: Market, useValue: { getUnusualActivity: () => of([]), ...service } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(MarketPulse);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('renders breadth, movers and sectors from the served summary', async () => {
    await setup({ getBreadth: () => of(breadth()) });
    const html = fixture.nativeElement as HTMLElement;

    expect(html.querySelector('.breadth-avg-value')?.textContent).toContain('1.25');
    expect(html.querySelectorAll('.sector-row').length).toBe(3);
    expect(html.querySelector('.mover-symbol')?.textContent?.trim()).toBe('UP');
  });

  it('splits the breadth bar over priced names only, so it always fills', async () => {
    await setup({ getBreadth: () => of(breadth()) });
    const segments = fixture.nativeElement.querySelectorAll('.breadth-bar .seg') as NodeListOf<HTMLElement>;

    const total = [...segments].reduce((sum, seg) => sum + parseFloat(seg.style.width), 0);
    // 3 up + 2 down + 1 flat = 6 priced; the 4 unpriced are excluded entirely.
    expect(Math.round(total)).toBe(100);
  });

  it('shows a sector with no priced name as "no data" rather than flat', async () => {
    await setup({ getBreadth: () => of(breadth()) });
    const rows = fixture.nativeElement.querySelectorAll('.sector-row');
    const materials = [...rows].find((row) => row.textContent.includes('Materials'));

    expect(materials.textContent).toContain('no data');
    // A null must not draw a bar at all — a zero-width one would still imply flat.
    expect(materials.querySelector('.sector-fill')).toBeNull();
  });

  it('scales sector bars to the widest absolute move in the set', async () => {
    await setup({ getBreadth: () => of(breadth()) });
    const fills = fixture.nativeElement.querySelectorAll('.sector-fill') as NodeListOf<HTMLElement>;

    // Widest is 4%; bars are half-width because they diverge from centre, so
    // the widest fills 50% of the track.
    expect(parseFloat(fills[0].style.width)).toBeCloseTo(50, 1);
    expect(parseFloat(fills[1].style.width)).toBeCloseTo(25, 1);
  });

  it('switches the movers list without refetching', async () => {
    await setup({ getBreadth: () => of(breadth()) });
    expect(fixture.nativeElement.querySelector('.mover-symbol').textContent.trim()).toBe('UP');

    const losersTab = [...fixture.nativeElement.querySelectorAll('.movers-tab')].find(
      (tab: HTMLElement) => tab.textContent?.trim() === 'Losers',
    ) as HTMLElement;
    losersTab.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.mover-symbol').textContent.trim()).toBe('DOWN');
  });

  it('reports a failure instead of rendering an empty shell', async () => {
    await setup({ getBreadth: () => throwError(() => new Error('down')) });

    expect(fixture.nativeElement.querySelector('.pulse-failed')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.breadth-bar')).toBeNull();
  });

  it('still renders breadth when the unusual-activity call fails', async () => {
    await setup({
      getBreadth: () => of(breadth()),
      getUnusualActivity: () => throwError(() => new Error('down')),
    });

    expect(fixture.nativeElement.querySelector('.breadth-bar')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.unusual-list')).toBeNull();
  });
});

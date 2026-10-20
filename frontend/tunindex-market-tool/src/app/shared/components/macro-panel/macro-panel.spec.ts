import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MacroSnapshot } from '../../../core/models/macro.model';
import { Market } from '../../../core/services/market';
import { MacroPanel } from './macro-panel';

function snapshot(overrides: Partial<MacroSnapshot> = {}): MacroSnapshot {
  return {
    rates: [
      {
        key: 'POLICY_RATE',
        label: 'Policy rate',
        note: 'The anchor for borrowing costs.',
        value: 7,
        unit: '%',
        periodLabel: 'au 03/09/2026',
        source: 'Banque Centrale de Tunisie',
        sourceUrl: 'https://www.bct.gov.tn',
      },
      {
        key: 'SAVINGS_RATE',
        label: 'Savings rate',
        note: null,
        value: 6,
        unit: '%',
        periodLabel: 'du mois de Septembre 2026',
        source: 'Banque Centrale de Tunisie',
        sourceUrl: 'https://www.bct.gov.tn',
      },
    ],
    economy: [
      {
        key: 'INFLATION_CPI',
        label: 'Inflation (CPI)',
        note: 'Erodes real returns.',
        value: 5.15,
        unit: '%',
        periodLabel: '2025',
        source: 'World Bank',
        sourceUrl: 'https://data.worldbank.org',
      },
    ],
    fetchedAt: '2026-09-04T14:40:00',
    unavailable: [],
    ...overrides,
  };
}

describe('MacroPanel', () => {
  let fixture: ComponentFixture<MacroPanel>;

  async function setup(service: Partial<Market>) {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [MacroPanel],
      providers: [provideZonelessChangeDetection(), { provide: Market, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(MacroPanel);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('shows every published figure with the period its publisher stated', async () => {
    await setup({ getMacro: () => of(snapshot()) });
    const periods = [...fixture.nativeElement.querySelectorAll('.macro-period')].map(
      (el: HTMLElement) => el.textContent?.trim(),
    );

    expect(fixture.nativeElement.querySelectorAll('.macro-item').length).toBe(3);
    // A rate without its period is misleading, not merely terse.
    expect(periods).toEqual(['au 03/09/2026', 'du mois de Septembre 2026', '2025']);
  });

  it('marks annual figures so they are not read as current', async () => {
    await setup({ getMacro: () => of(snapshot()) });
    const annual = fixture.nativeElement.querySelectorAll('.macro-item.annual');

    expect(annual.length).toBe(1);
    expect(annual[0].textContent).toContain('annual');
  });

  it('gives the policy rate the headline treatment, and only it', async () => {
    await setup({ getMacro: () => of(snapshot()) });

    const headlines = fixture.nativeElement.querySelectorAll('.macro-item.headline');
    expect(headlines.length).toBe(1);
    expect(headlines[0].textContent).toContain('Policy rate');
  });

  it('derives the real policy rate and reads it correctly', async () => {
    await setup({ getMacro: () => of(snapshot()) });
    const real = fixture.nativeElement.querySelector('.macro-real')?.textContent ?? '';

    // 7.00 - 5.15 = +1.85
    expect(real).toContain('1.85');
    expect(real).toContain('cash beats inflation');
  });

  it('flips the reading when inflation outruns the policy rate', async () => {
    const hot = snapshot();
    hot.economy[0].value = 9.5;
    await setup({ getMacro: () => of(hot) });

    expect(fixture.nativeElement.querySelector('.macro-real')?.textContent)
      .toContain('cash loses value');
  });

  it('omits the real rate rather than guessing when inflation is missing', async () => {
    await setup({ getMacro: () => of(snapshot({ economy: [] })) });

    expect(fixture.nativeElement.querySelector('.macro-real')).toBeNull();
  });

  it('names a publisher it could not reach instead of quietly showing less', async () => {
    await setup({ getMacro: () => of(snapshot({ unavailable: ['World Bank'] })) });

    expect(fixture.nativeElement.querySelector('.macro-warn')?.textContent).toContain('World Bank');
  });

  it('reports a failed lookup', async () => {
    await setup({ getMacro: () => throwError(() => new Error('down')) });

    expect(fixture.nativeElement.querySelector('.macro-note')?.textContent).toContain('unavailable');
  });
});

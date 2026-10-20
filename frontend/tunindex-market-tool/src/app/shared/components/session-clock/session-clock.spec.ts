import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MarketSession } from '../../../core/models/market.model';
import { Market } from '../../../core/services/market';
import { SessionClock } from './session-clock';

function session(overrides: Partial<MarketSession> = {}): MarketSession {
  return {
    state: 'OPEN',
    label: 'Continuous trading',
    nextTransitionLabel: 'Closes',
    nextTransitionAt: '2026-09-04T14:00:00',
    secondsUntilTransition: 3725,
    tunisTime: '2026-09-04T12:57:55',
    timezone: 'Africa/Tunis',
    scheduleBased: true,
    ...overrides,
  };
}

describe('SessionClock', () => {
  let fixture: ComponentFixture<SessionClock>;

  async function setup(service: Partial<Market>) {
    // Reset first: a couple of these tests configure the module twice to
    // compare two server responses, which TestBed otherwise rejects.
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [SessionClock],
      providers: [provideZonelessChangeDetection(), { provide: Market, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(SessionClock);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('shows the state and what happens next', async () => {
    await setup({ getSession: () => of(session()) });
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Open');
    expect(text).toContain('Closes');
  });

  it('formats an hours-away transition as hours and minutes', async () => {
    await setup({ getSession: () => of(session()) });
    // 3725s = 1h 02m; seconds are noise at this range.
    expect(fixture.nativeElement.querySelector('.session-countdown')?.textContent?.trim()).toBe('1h 02m');
  });

  it('switches to days when the gap spans a weekend', async () => {
    await setup({ getSession: () => of(session({ state: 'WEEKEND', secondsUntilTransition: 237600 })) });
    // 66h — "66:00:00" would be unreadable.
    expect(fixture.nativeElement.querySelector('.session-countdown')?.textContent?.trim()).toBe('2d 18h');
  });

  it('counts down to the minute inside the last hour', async () => {
    await setup({ getSession: () => of(session({ secondsUntilTransition: 125 })) });
    expect(fixture.nativeElement.querySelector('.session-countdown')?.textContent?.trim()).toBe('2:05');
  });

  it('only marks a genuinely open market as live', async () => {
    await setup({ getSession: () => of(session()) });
    expect(fixture.nativeElement.querySelector('.session-dot')?.classList).toContain('live');

    await setup({ getSession: () => of(session({ state: 'PRE_OPEN', label: 'Pre-opening' })) });
    const dot = fixture.nativeElement.querySelector('.session-dot');
    expect(dot?.classList).not.toContain('live');
    expect(fixture.nativeElement.querySelector('.session')?.classList).toContain('auction');
  });

  it('renders nothing at all when the session lookup fails', async () => {
    await setup({ getSession: () => throwError(() => new Error('down')) });
    // A permanent error badge beside the brand would be worse than silence.
    expect(fixture.nativeElement.querySelector('.session')).toBeNull();
  });
});

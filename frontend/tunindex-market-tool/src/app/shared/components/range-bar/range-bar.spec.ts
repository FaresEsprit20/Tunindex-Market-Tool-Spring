import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RangeBar } from './range-bar';

describe('RangeBar', () => {
  let fixture: ComponentFixture<RangeBar>;

  async function render(min: number | null, max: number | null, current: number | null) {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({ imports: [RangeBar] }).compileComponents();
    fixture = TestBed.createComponent(RangeBar);
    fixture.componentRef.setInput('min', min);
    fixture.componentRef.setInput('max', max);
    fixture.componentRef.setInput('current', current);
    fixture.componentRef.setInput('minLabel', String(min ?? '—'));
    fixture.componentRef.setInput('maxLabel', String(max ?? '—'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function marker(): HTMLElement | null {
    return fixture.nativeElement.querySelector('.range-marker');
  }

  it('should create', async () => {
    await render(0, 100, 50);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('places the marker at the measured position', async () => {
    await render(0, 100, 50);
    expect(marker()?.style.left).toBe('50%');
  });

  it('renders a stock sitting exactly on its low, rather than as no data', async () => {
    // The bug this guards: 0 is falsy, so `@if (pct(); as p)` sent every
    // stock at the bottom of its range into the empty branch — including any
    // name at its 52-week low, which is the case the scorer cares about most.
    await render(91.15, 92.0, 91.15);

    expect(marker()).not.toBeNull();
    expect(marker()?.style.left).toBe('0%');
    expect(fixture.nativeElement.querySelector('.range-track.empty')).toBeNull();
  });

  it('renders a stock sitting exactly on its high', async () => {
    await render(44.45, 100, 100);

    expect(marker()?.style.left).toBe('100%');
  });

  it('shows both bounds as labels when not compact', async () => {
    await render(44.45, 100, 44.45);
    const labels = [...fixture.nativeElement.querySelectorAll('.range-labels span')].map(
      (el: HTMLElement) => el.textContent?.trim(),
    );

    expect(labels).toEqual(['44.45', '100']);
  });

  it('falls back to an empty track when the range has no width', async () => {
    // A zero-width range has no position to report — unlike a zero position.
    await render(50, 50, 50);

    expect(marker()).toBeNull();
    expect(fixture.nativeElement.querySelector('.range-track.empty')).not.toBeNull();
  });

  it('falls back to an empty track when a bound is missing', async () => {
    await render(null, 100, 50);
    expect(marker()).toBeNull();

    await render(10, 100, null);
    expect(marker()).toBeNull();
  });

  it('clamps a value outside its own range instead of overflowing the track', async () => {
    await render(10, 20, 25);
    expect(marker()?.style.left).toBe('100%');

    await render(10, 20, 5);
    expect(marker()?.style.left).toBe('0%');
  });
});

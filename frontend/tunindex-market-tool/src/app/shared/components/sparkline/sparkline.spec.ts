import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Sparkline } from './sparkline';

describe('Sparkline', () => {
  let fixture: ComponentFixture<Sparkline>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Sparkline] }).compileComponents();
    fixture = TestBed.createComponent(Sparkline);
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('draws nothing for a series too short to plot', async () => {
    fixture.componentRef.setInput('points', [10]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('svg')).toBeNull();
  });

  it('draws a trace for a real series', async () => {
    fixture.componentRef.setInput('points', [10, 11, 9, 12]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('svg')).toBeTruthy();
  });

  it('survives a flat series without dividing by a zero range', async () => {
    fixture.componentRef.setInput('points', [5, 5, 5]);
    await fixture.whenStable();
    const path = fixture.nativeElement.querySelector('path')?.getAttribute('d') ?? '';
    expect(path).not.toContain('NaN');
  });
});

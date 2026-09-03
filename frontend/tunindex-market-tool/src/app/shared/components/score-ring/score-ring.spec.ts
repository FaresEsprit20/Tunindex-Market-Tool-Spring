import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ScoreRing } from './score-ring';
import { OpportunityScore } from '../../../core/models/opportunity.model';

const base: OpportunityScore = {
  symbol: 'TEST', name: 'Test', sector: 'BANKING', lastPrice: 10, currency: 'TND',
  overallScore: 72, verdict: 'BUY',
  valuationScore: 90, timingScore: 60, financialHealthScore: 80,
  incomeScore: 40, momentumScore: null, newsScore: 50,
  dataCompleteness: 83, reasons: [], warnings: [],
};

describe('ScoreRing', () => {
  let fixture: ComponentFixture<ScoreRing>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ScoreRing] }).compileComponents();
    fixture = TestBed.createComponent(ScoreRing);
  });

  it('draws one track and one fill per component', async () => {
    fixture.componentRef.setInput('score', base);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('.arc-track').length).toBe(6);
    expect(fixture.nativeElement.querySelectorAll('.arc-fill').length).toBe(6);
  });

  it('renders a missing component as transparent rather than zero-scored', async () => {
    fixture.componentRef.setInput('score', base);
    await fixture.whenStable();
    const fills = [...fixture.nativeElement.querySelectorAll('.arc-fill')] as Element[];
    expect(fills.some((f) => f.classList.contains('none'))).toBe(true);
  });

  it('shows the overall score in the centre', async () => {
    fixture.componentRef.setInput('score', base);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.ring-value').textContent.trim()).toBe('72');
  });
});

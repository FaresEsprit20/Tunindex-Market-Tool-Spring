import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TechnicalAnalysis } from './technical-analysis';

describe('TechnicalAnalysis', () => {
  let component: TechnicalAnalysis;
  let fixture: ComponentFixture<TechnicalAnalysis>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TechnicalAnalysis],
    }).compileComponents();

    fixture = TestBed.createComponent(TechnicalAnalysis);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

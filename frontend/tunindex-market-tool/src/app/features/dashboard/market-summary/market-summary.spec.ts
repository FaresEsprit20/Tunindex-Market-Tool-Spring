import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MarketSummary } from './market-summary';

describe('MarketSummary', () => {
  let component: MarketSummary;
  let fixture: ComponentFixture<MarketSummary>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarketSummary],
    }).compileComponents();

    fixture = TestBed.createComponent(MarketSummary);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

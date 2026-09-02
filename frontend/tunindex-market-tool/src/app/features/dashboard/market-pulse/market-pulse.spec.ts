import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MarketPulse } from './market-pulse';

describe('MarketPulse', () => {
  let component: MarketPulse;
  let fixture: ComponentFixture<MarketPulse>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarketPulse],
    }).compileComponents();

    fixture = TestBed.createComponent(MarketPulse);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

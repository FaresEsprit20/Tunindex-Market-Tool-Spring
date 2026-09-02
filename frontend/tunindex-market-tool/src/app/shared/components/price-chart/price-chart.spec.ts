import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PriceChart } from './price-chart';

describe('PriceChart', () => {
  let component: PriceChart;
  let fixture: ComponentFixture<PriceChart>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PriceChart],
    }).compileComponents();

    fixture = TestBed.createComponent(PriceChart);
    fixture.componentRef.setInput('points', []);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

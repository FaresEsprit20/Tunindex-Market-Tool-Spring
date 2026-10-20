import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StockStatistics } from './stock-statistics';

describe('StockStatistics', () => {
  let component: StockStatistics;
  let fixture: ComponentFixture<StockStatistics>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StockStatistics],
    }).compileComponents();

    fixture = TestBed.createComponent(StockStatistics);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

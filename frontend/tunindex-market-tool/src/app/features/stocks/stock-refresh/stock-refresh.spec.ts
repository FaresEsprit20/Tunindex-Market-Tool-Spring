import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StockRefresh } from './stock-refresh';

describe('StockRefresh', () => {
  let component: StockRefresh;
  let fixture: ComponentFixture<StockRefresh>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StockRefresh],
    }).compileComponents();

    fixture = TestBed.createComponent(StockRefresh);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

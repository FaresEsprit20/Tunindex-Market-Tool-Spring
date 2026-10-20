import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StockFilter } from './stock-filter';

describe('StockFilter', () => {
  let component: StockFilter;
  let fixture: ComponentFixture<StockFilter>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StockFilter],
    }).compileComponents();

    fixture = TestBed.createComponent(StockFilter);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

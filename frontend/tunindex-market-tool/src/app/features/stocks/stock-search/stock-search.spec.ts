import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StockSearch } from './stock-search';

describe('StockSearch', () => {
  let component: StockSearch;
  let fixture: ComponentFixture<StockSearch>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StockSearch],
    }).compileComponents();

    fixture = TestBed.createComponent(StockSearch);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

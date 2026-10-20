import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StockCard } from './stock-card';

describe('StockCard', () => {
  let component: StockCard;
  let fixture: ComponentFixture<StockCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StockCard],
    }).compileComponents();

    fixture = TestBed.createComponent(StockCard);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

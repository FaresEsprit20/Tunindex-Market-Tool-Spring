import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WatchlistStar } from './watchlist-star';

describe('WatchlistStar', () => {
  let component: WatchlistStar;
  let fixture: ComponentFixture<WatchlistStar>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WatchlistStar],
    }).compileComponents();

    fixture = TestBed.createComponent(WatchlistStar);
    fixture.componentRef.setInput('symbol', 'BIAT');
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

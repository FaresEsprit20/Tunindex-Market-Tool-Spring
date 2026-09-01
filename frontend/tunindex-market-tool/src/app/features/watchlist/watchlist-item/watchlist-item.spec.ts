import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WatchlistItem } from './watchlist-item';

describe('WatchlistItem', () => {
  let component: WatchlistItem;
  let fixture: ComponentFixture<WatchlistItem>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WatchlistItem],
    }).compileComponents();

    fixture = TestBed.createComponent(WatchlistItem);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

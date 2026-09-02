import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Watchlist } from './watchlist';

describe('Watchlist', () => {
  let component: Watchlist;
  let fixture: ComponentFixture<Watchlist>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Watchlist],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Watchlist);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

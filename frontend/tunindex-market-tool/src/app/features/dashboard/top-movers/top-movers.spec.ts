import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TopMovers } from './top-movers';

describe('TopMovers', () => {
  let component: TopMovers;
  let fixture: ComponentFixture<TopMovers>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TopMovers],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TopMovers);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MarketNews } from './market-news';

describe('MarketNews', () => {
  let component: MarketNews;
  let fixture: ComponentFixture<MarketNews>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarketNews],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MarketNews);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

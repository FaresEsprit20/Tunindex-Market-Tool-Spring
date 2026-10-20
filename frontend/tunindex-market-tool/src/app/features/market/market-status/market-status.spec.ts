import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MarketStatus } from './market-status';

describe('MarketStatus', () => {
  let component: MarketStatus;
  let fixture: ComponentFixture<MarketStatus>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarketStatus],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(MarketStatus);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

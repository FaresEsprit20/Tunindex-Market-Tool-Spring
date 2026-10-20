import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Stock } from './stock';

describe('Stock', () => {
  let service: Stock;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(Stock);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});

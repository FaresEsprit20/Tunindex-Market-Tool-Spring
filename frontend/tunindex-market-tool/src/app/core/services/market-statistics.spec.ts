import { TestBed } from '@angular/core/testing';
import { MarketStatistics } from './market-statistics';

describe('MarketStatistics', () => {
  let service: MarketStatistics;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(MarketStatistics);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});

import { TestBed } from '@angular/core/testing';
import { AccountManagement } from './account-management';

describe('AccountManagement', () => {
  let service: AccountManagement;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AccountManagement);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});

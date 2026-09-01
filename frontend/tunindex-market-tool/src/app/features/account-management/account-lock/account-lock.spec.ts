import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountLock } from './account-lock';

describe('AccountLock', () => {
  let component: AccountLock;
  let fixture: ComponentFixture<AccountLock>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountLock],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountLock);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthenticationStatus } from './authentication-status';

describe('AuthenticationStatus', () => {
  let component: AuthenticationStatus;
  let fixture: ComponentFixture<AuthenticationStatus>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthenticationStatus],
    }).compileComponents();

    fixture = TestBed.createComponent(AuthenticationStatus);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

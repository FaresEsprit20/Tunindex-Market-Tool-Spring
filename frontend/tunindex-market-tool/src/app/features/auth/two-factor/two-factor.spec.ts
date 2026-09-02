import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TwoFactor } from './two-factor';

describe('TwoFactor', () => {
  let component: TwoFactor;
  let fixture: ComponentFixture<TwoFactor>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TwoFactor],
      providers: [provideRouter([{ path: 'auth/login', component: TwoFactor }]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(TwoFactor);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

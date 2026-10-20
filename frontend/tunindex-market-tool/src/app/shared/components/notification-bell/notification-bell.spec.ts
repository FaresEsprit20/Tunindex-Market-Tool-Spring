import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationBell } from './notification-bell';

describe('NotificationBell', () => {
  let component: NotificationBell;
  let fixture: ComponentFixture<NotificationBell>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationBell],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationBell);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

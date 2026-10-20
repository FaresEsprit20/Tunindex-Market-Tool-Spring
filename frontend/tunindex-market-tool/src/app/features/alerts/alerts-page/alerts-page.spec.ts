import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AlertsPage } from './alerts-page';

describe('AlertsPage', () => {
  let component: AlertsPage;
  let fixture: ComponentFixture<AlertsPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AlertsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AlertsPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

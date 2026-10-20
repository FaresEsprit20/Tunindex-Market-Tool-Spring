import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Opportunities } from './opportunities';

describe('Opportunities', () => {
  let component: Opportunities;
  let fixture: ComponentFixture<Opportunities>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Opportunities],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Opportunities);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

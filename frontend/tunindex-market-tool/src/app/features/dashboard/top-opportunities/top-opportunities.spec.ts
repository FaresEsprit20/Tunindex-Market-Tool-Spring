import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TopOpportunities } from './top-opportunities';

describe('TopOpportunities', () => {
  let component: TopOpportunities;
  let fixture: ComponentFixture<TopOpportunities>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TopOpportunities],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TopOpportunities);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

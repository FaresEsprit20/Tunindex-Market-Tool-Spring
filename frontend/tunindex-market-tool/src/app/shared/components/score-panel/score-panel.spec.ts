import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ScorePanel } from './score-panel';

describe('ScorePanel', () => {
  let component: ScorePanel;
  let fixture: ComponentFixture<ScorePanel>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScorePanel],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(ScorePanel);
    fixture.componentRef.setInput('symbol', 'BIAT');
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

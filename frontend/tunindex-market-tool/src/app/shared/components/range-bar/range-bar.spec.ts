import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RangeBar } from './range-bar';

describe('RangeBar', () => {
  let component: RangeBar;
  let fixture: ComponentFixture<RangeBar>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RangeBar],
    }).compileComponents();

    fixture = TestBed.createComponent(RangeBar);
    fixture.componentRef.setInput('min', 0);
    fixture.componentRef.setInput('max', 100);
    fixture.componentRef.setInput('current', 50);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

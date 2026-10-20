import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OwnershipDistribution } from './ownership-distribution';

describe('OwnershipDistribution', () => {
  let component: OwnershipDistribution;
  let fixture: ComponentFixture<OwnershipDistribution>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OwnershipDistribution],
    }).compileComponents();

    fixture = TestBed.createComponent(OwnershipDistribution);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

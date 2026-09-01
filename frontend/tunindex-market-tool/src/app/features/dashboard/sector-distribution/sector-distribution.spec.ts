import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SectorDistribution } from './sector-distribution';

describe('SectorDistribution', () => {
  let component: SectorDistribution;
  let fixture: ComponentFixture<SectorDistribution>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SectorDistribution],
    }).compileComponents();

    fixture = TestBed.createComponent(SectorDistribution);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

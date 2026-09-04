import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SplitPane } from './split-pane';

describe('SplitPane', () => {
  let fixture: ComponentFixture<SplitPane>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({ imports: [SplitPane] }).compileComponents();
    fixture = TestBed.createComponent(SplitPane);
  });

  it('should create', async () => {
    await fixture.whenStable();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders a divider with separator semantics', async () => {
    await fixture.whenStable();
    const divider = fixture.nativeElement.querySelector('.divider');
    expect(divider.getAttribute('role')).toBe('separator');
    expect(divider.getAttribute('tabindex')).toBe('0');
  });

  it('sizes pane A from the initial ratio', async () => {
    fixture.componentRef.setInput('initialRatio', 0.4);
    await fixture.whenStable();
    const paneA = fixture.nativeElement.querySelector('.pane-a') as HTMLElement;
    expect(paneA.style.flexBasis).toBe('40%');
  });

  it('restores a stored ratio over the initial one', async () => {
    localStorage.setItem('tunindex-split-test-key', '0.35');
    const restored = TestBed.createComponent(SplitPane);
    restored.componentRef.setInput('storageKey', 'test-key');
    restored.componentRef.setInput('initialRatio', 0.7);
    await restored.whenStable();
    const paneA = restored.nativeElement.querySelector('.pane-a') as HTMLElement;
    expect(paneA.style.flexBasis).toBe('35%');
  });
});

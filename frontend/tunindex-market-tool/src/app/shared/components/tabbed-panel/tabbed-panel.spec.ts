import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TabbedPanel } from './tabbed-panel';
import { Tab } from './tab';

@Component({
  imports: [TabbedPanel, Tab],
  template: `
    <app-tabbed-panel storageKey="spec">
      <ng-template appTab="One"><p class="one">first</p></ng-template>
      <ng-template appTab="Two"><p class="two">second</p></ng-template>
    </app-tabbed-panel>
  `,
})
class Host {}

describe('TabbedPanel', () => {
  let fixture: ComponentFixture<Host>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
  });

  it('renders a tab per template and opens the first', () => {
    expect(fixture.nativeElement.querySelectorAll('.tab').length).toBe(2);
    expect(fixture.nativeElement.querySelector('.tab.active').textContent.trim()).toBe('One');
    expect(fixture.nativeElement.querySelector('.one')).toBeTruthy();
  });

  it('only instantiates the active tab', () => {
    expect(fixture.nativeElement.querySelector('.two')).toBeNull();
  });

  it('switches content on click and remembers the choice', async () => {
    const tabs = fixture.nativeElement.querySelectorAll('.tab') as NodeListOf<HTMLElement>;
    tabs[1].click();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.two')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.one')).toBeNull();
    expect(localStorage.getItem('tunindex-tab-spec')).toBe('Two');
  });
});

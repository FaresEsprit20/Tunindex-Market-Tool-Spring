import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AppNotification } from '../../../core/models/alert.model';
import { Alerts } from '../../../core/services/alerts';
import { ActivityFeed } from './activity-feed';

function note(overrides: Partial<AppNotification> = {}): AppNotification {
  return {
    id: 1,
    title: 'BIAT is up 3.20%',
    body: 'Banque Internationale Arabe de Tunisie is trading at 161.40 TND.',
    category: 'WATCHLIST',
    tone: 'POSITIVE',
    symbol: 'BIAT',
    read: false,
    createdAt: '2026-09-04T14:00:00',
    ...overrides,
  };
}

describe('ActivityFeed', () => {
  let fixture: ComponentFixture<ActivityFeed>;

  async function setup(items: AppNotification[]) {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ActivityFeed],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {
          provide: Alerts,
          useValue: {
            notifications: signal(items),
            refreshNotifications: () => of(items),
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ActivityFeed);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('lists what the platform has told the user', async () => {
    await setup([note(), note({ id: 2, category: 'TRADE', title: 'Buy order filled · AB' })]);

    expect(fixture.nativeElement.querySelectorAll('.feed-item').length).toBe(2);
    expect(fixture.nativeElement.querySelector('.feed-title')?.textContent).toContain('BIAT');
  });

  it('only offers tabs for categories that are actually present', async () => {
    await setup([note({ category: 'TRADE' }), note({ id: 2, category: 'TRADE' })]);
    const tabs = [...fixture.nativeElement.querySelectorAll('.feed-tab')] as HTMLElement[];

    // One category present means no meaningful filter, so no tab row at all.
    expect(tabs.length).toBe(0);
  });

  it('filters to a category without refetching', async () => {
    await setup([
      note({ id: 1, category: 'TRADE', title: 'Buy order filled · AB' }),
      note({ id: 2, category: 'ALERT', title: 'SFBT crossed 50' }),
    ]);

    const alertTab = [...fixture.nativeElement.querySelectorAll('.feed-tab')].find(
      (tab: HTMLElement) => tab.textContent?.includes('Alerts'),
    ) as HTMLElement;
    alertTab.click();
    fixture.detectChanges();

    const titles = [...fixture.nativeElement.querySelectorAll('.feed-title')].map(
      (el: HTMLElement) => el.textContent?.trim(),
    );
    expect(titles).toEqual(['SFBT crossed 50']);
  });

  it('marks tone on the rail rather than colouring the whole row', async () => {
    await setup([note({ tone: 'NEGATIVE' })]);
    const rail = fixture.nativeElement.querySelector('.feed-rail');

    expect(rail?.classList).toContain('negative');
    expect(fixture.nativeElement.querySelector('.feed-item')?.classList).not.toContain('negative');
  });

  it('explains what will appear here when there is nothing yet', async () => {
    await setup([]);

    expect(fixture.nativeElement.querySelector('.feed-empty')?.textContent).toContain('watchlist');
    expect(fixture.nativeElement.querySelector('.feed-list')).toBeNull();
  });

  it('links a symbol back to its stock page', async () => {
    await setup([note({ symbol: 'AB' })]);
    const link = fixture.nativeElement.querySelector('.feed-symbol') as HTMLAnchorElement;

    expect(link.textContent?.trim()).toBe('AB');
    expect(link.getAttribute('href')).toBe('/app/stocks/AB');
  });
});

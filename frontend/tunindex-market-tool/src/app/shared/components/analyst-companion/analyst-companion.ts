import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Market } from '../../../core/services/market';
import { Stock } from '../../../core/services/stock';
import { MarketSession } from '../../../core/models/market.model';
import { OpportunityScore } from '../../../core/models/opportunity.model';

/** Where the companion is speaking from; it says different things on each. */
export type CompanionContext = 'dashboard' | 'opportunities';

/**
 * The Tradify Analyst, speaking directly to the reader.
 *
 * <p>The point is to remove the feeling of facing a wall of figures alone -
 * so it behaves like someone who was already here when you arrived and has
 * been through the list. That only works if it says what it actually did:
 * how many companies it read, what it found, what it is waiting for. A
 * greeting assembled from real counts reads as presence; "Welcome! Let's find
 * opportunities!" reads as a popup, and the second one costs trust rather
 * than building it.
 *
 * <p>It also has to be willing to say there is nothing today. A guide who is
 * enthusiastic every single morning is one you stop listening to by the
 * second week, and on a finance screen that enthusiasm is actively harmful.
 */
@Component({
  selector: 'app-analyst-companion',
  imports: [RouterLink],
  templateUrl: './analyst-companion.html',
  styleUrl: './analyst-companion.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalystCompanion implements OnDestroy {
  private readonly market = inject(Market);
  private readonly stock = inject(Stock);

  readonly context = input<CompanionContext>('dashboard');

  protected readonly loading = signal(true);
  protected readonly session = signal<MarketSession | null>(null);
  protected readonly universe = signal<number | null>(null);
  protected readonly rows = signal<OpportunityScore[]>([]);

  /** How much of the message has been revealed, for the typing effect. */
  protected readonly typed = signal(0);
  private timer?: ReturnType<typeof setInterval>;

  constructor() {
    forkJoin({
      session: this.market.getSession().pipe(catchError(() => of(null))),
      breadth: this.market.getBreadth().pipe(catchError(() => of(null))),
      opportunities: this.stock.getOpportunities(20, 0).pipe(catchError(() => of([]))),
    }).subscribe(({ session, breadth, opportunities }) => {
      this.session.set(session);
      this.universe.set(breadth?.total ?? null);
      this.rows.set(opportunities);
      this.loading.set(false);
      this.startTyping();
    });
  }

  // ── What it says ─────────────────────────────────────────────────────

  private readonly liveEntries = computed(() =>
    this.rows().filter((r) => r.tradeSetup?.stance === 'ACCUMULATE_NOW'),
  );

  private readonly waiting = computed(() =>
    this.rows().filter((r) => r.tradeSetup?.stance === 'BUY_THE_DIP'),
  );

  private readonly watching = computed(() =>
    this.rows().filter((r) => r.tradeSetup?.stance === 'WAIT_FOR_CONFIRMATION'),
  );

  /**
   * Time of day in Tunis, not in the reader's browser.
   *
   * <p>A greeting that says "good morning" to someone watching the Tunis close
   * from another timezone is the sort of small wrongness that gives away that
   * nobody is really there.
   */
  protected readonly greeting = computed(() => {
    const tunis = this.session()?.tunisTime;
    const hour = tunis ? Number(tunis.slice(11, 13)) : new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  });

  /** One line on where the market is right now. */
  protected readonly marketLine = computed(() => {
    const s = this.session();
    if (!s) {
      return 'I could not read the exchange session just now.';
    }
    switch (s.state) {
      case 'OPEN':
        return 'The BVMT is open and trading.';
      case 'PRE_OPEN':
        return 'The BVMT is in pre-opening.';
      case 'PRE_CLOSE':
        return 'The BVMT is about to close.';
      case 'WEEKEND':
        return 'The exchange is shut for the weekend, so these are Friday’s closing figures.';
      default:
        return `The exchange is closed${s.nextTransitionLabel ? ` — next up, ${s.nextTransitionLabel.toLowerCase()}` : ''}.`;
    }
  });

  /**
   * The body of what it has to say, assembled from real counts.
   *
   * <p>Every number here was computed before the reader arrived, which is the
   * whole basis for the tone: it is reporting, not greeting.
   */
  protected readonly message = computed(() => {
    const total = this.universe();
    const live = this.liveEntries().length;
    const dip = this.waiting().length;
    const watch = this.watching().length;
    const scanned = total ? `I went through all ${total} listed companies` : 'I went through the whole exchange';

    if (this.context() === 'opportunities') {
      if (live > 0) {
        const names = this.liveEntries().slice(0, 2).map((r) => r.symbol).join(' and ');
        return `${scanned} and ranked what is worth owning. ${live === 1 ? 'One name is' : `${live} names are`} inside the buy zone right now — ${names}. The rest are good businesses at prices I would not pay yet.`;
      }
      if (dip > 0) {
        return `${scanned}. Nothing is at a price I would buy today, but ${dip === 1 ? 'one is' : `${dip} are`} close. Open a card to see the level I am waiting for.`;
      }
      return `${scanned} and ranked them. Nothing is near an entry today — which is an answer, not a gap.`;
    }

    // Dashboard
    if (live > 0) {
      const best = this.liveEntries()[0];
      return `${scanned} this morning. ${live === 1 ? 'One is' : `${live} are`} trading inside its buy zone — ${best.symbol} is the one I would look at first. ${watch + dip} more are on my watchlist.`;
    }
    if (dip > 0 || watch > 0) {
      return `${scanned} this morning. Nothing is at a price worth buying today, but I am watching ${dip + watch} of them and I will tell you when one reaches its level.`;
    }
    return `${scanned} this morning. There is nothing worth acting on today — I would rather tell you that than invent something.`;
  });

  /** The full spoken line, which the typing effect reveals. */
  protected readonly fullText = computed(() => `${this.marketLine()} ${this.message()}`);

  protected readonly visibleText = computed(() => this.fullText().slice(0, this.typed()));

  protected readonly finished = computed(() => this.typed() >= this.fullText().length);

  /** Something to act on, when there is something. */
  protected readonly topSymbol = computed(() => this.liveEntries()[0]?.symbol ?? null);

  // ── Presence ─────────────────────────────────────────────────────────

  /**
   * Reveals the text a few characters at a time.
   *
   * <p>Fast enough not to make anyone wait - the whole line lands in about a
   * second - but present enough that the words arrive rather than appear,
   * which is most of what makes it read as someone speaking.
   */
  private startTyping(): void {
    const text = this.fullText();
    if (this.prefersReducedMotion()) {
      this.typed.set(text.length);
      return;
    }
    this.stopTyping();
    this.timer = setInterval(() => {
      const next = this.typed() + 3;
      if (next >= text.length) {
        this.typed.set(text.length);
        this.stopTyping();
      } else {
        this.typed.set(next);
      }
    }, 16);
  }

  /** Skips to the end; nobody should have to wait for an animation. */
  protected reveal(): void {
    this.stopTyping();
    this.typed.set(this.fullText().length);
  }

  private prefersReducedMotion(): boolean {
    return typeof window !== 'undefined'
      && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true;
  }

  private stopTyping(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }

  ngOnDestroy(): void {
    this.stopTyping();
  }
}

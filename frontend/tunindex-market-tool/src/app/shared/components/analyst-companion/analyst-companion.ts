import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnDestroy,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AnalystJournal } from '../../../core/services/analyst-journal';
import { Market } from '../../../core/services/market';
import { Stock } from '../../../core/services/stock';
import { User } from '../../../core/services/user';
import { Watchlist } from '../../../core/services/watchlist';
import { SettledCall } from '../../../core/models/analyst-call.model';
import { AnalystPanel } from '../analyst-panel/analyst-panel';
import { MarketSession } from '../../../core/models/market.model';
import { StockDto } from '../../../core/models/stock.model';
import { OpportunityScore, VERDICT_LABELS, Verdict } from '../../../core/models/opportunity.model';
import { STANCE_LABELS } from '../../../core/models/trade-setup.model';

/**
 * The scorer's own call, ordered.
 *
 * <p>Needed because a verdict is not a score: a STRONG_BUY at 74 is a
 * stronger recommendation than a BUY at 78, and ranking on the number alone
 * throws away the judgement the scorer already made.
 */
const VERDICT_RANK: Record<Verdict, number> = {
  STRONG_BUY: 4,
  BUY: 3,
  WATCH: 2,
  HOLD: 1,
  AVOID: 0,
};

/** Where the companion is speaking from; it says different things on each. */
export type CompanionContext = 'dashboard' | 'opportunities';

/**
 * The analyst as a person, not a panel.
 *
 * <p>A desk has a name on it. The whole point of the feature is that somebody
 * is watching the market so the reader does not have to, and "somebody" is not
 * a feeling a logo produces — a name, a seat, and a beat of hesitation before
 * answering do. Held here rather than in the template so the voice stays
 * consistent everywhere it appears.
 */
const ANALYST = {
  /** How the desk introduces itself, on the nameplate. */
  fullName: 'Mr Skylar Graham',
  /**
   * Inline, wherever the full name would read stiffly — "Ask Mr Graham"
   * rather than "Ask Mr Skylar Graham". Surnames are how a desk is actually
   * referred to once the introduction is out of the way.
   */
  shortName: 'Mr Graham',
  role: 'Senior Analyst',
  desk: 'BVMT coverage desk',
  initials: 'SG',
} as const;

/** One company, reduced to what the scan strip draws. */
interface ScanBar {
  symbol: string;
  changePct: number | null;
  /** Bar height 0-1, from the size of the move rather than its direction. */
  magnitude: number;
  found: boolean;
}

/** A turn in the conversation. */
interface Line {
  id: string;
  from: 'analyst' | 'you';
  text: string;
  /** Rendered larger — the one sentence that carries the finding. */
  emphasis?: boolean;
}

/** A question the reader can put back, answered from data already loaded. */
interface FollowUp {
  id: string;
  label: string;
}

/**
 * Mr Graham, the Tradify analyst, delivering a briefing and taking questions.
 *
 * <p>Built around one idea: <em>show the work, then talk about it</em>. It
 * opens by sweeping a bar for every listed company — each bar's height and
 * colour is that company's real move today — and only then starts speaking.
 * By the time the desk says "I read all 86", the reader has watched it happen.
 * That is what a claim of diligence has to be backed by; a card that types out
 * a sentence is a chat bubble with extra steps.
 *
 * <p>The reader can then ask things back, and the answers are assembled from
 * the same payloads already in memory — no second round trip, and nothing said
 * that is not in the data. That is what makes it a conversation rather than a
 * slideshow, and it is where the "someone has your back" feeling actually
 * comes from: not from the avatar, but from asking "why?" and getting a real
 * answer with real numbers in it.
 */
@Component({
  selector: 'app-analyst-companion',
  imports: [RouterLink, AnalystPanel],
  templateUrl: './analyst-companion.html',
  styleUrl: './analyst-companion.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalystCompanion implements OnDestroy {
  private readonly market = inject(Market);
  private readonly stock = inject(Stock);
  private readonly user = inject(User);
  private readonly journal = inject(AnalystJournal);
  private readonly watchlist = inject(Watchlist);
  private readonly destroyRef = inject(DestroyRef);

  readonly context = input<CompanionContext>('dashboard');

  protected readonly analyst = ANALYST;

  /** reading -> speaking -> ready. Drives the avatar and the strip. */
  protected readonly phase = signal<'loading' | 'reading' | 'speaking' | 'ready'>('loading');

  protected readonly session = signal<MarketSession | null>(null);
  protected readonly bars = signal<ScanBar[]>([]);

  /**
   * Scored names, or null when the scoring desk could not be reached.
   *
   * <p>The distinction matters more than it looks. An empty list means "I
   * checked, and there is nothing"; null means "I could not check". Saying
   * the first when the second is true is a confident lie, and it is the exact
   * failure this component exists to avoid — a reader who is told there is no
   * opportunity today will not go looking for one.
   */
  private readonly scored = signal<OpportunityScore[] | null>([]);

  /** What the exchange lists, per breadth. Null when breadth did not answer. */
  private readonly listedTotal = signal<number | null>(null);
  /** True when the company list itself failed to load — nothing was read. */
  private readonly listFailed = signal(false);

  /** How far the sweep has travelled, 0-100. */
  protected readonly scanPct = signal(0);
  /** Companies read so far, counting up beside the strip. */
  protected readonly scanned = signal(0);

  /** The conversation so far. Grows as the briefing plays and questions land. */
  protected readonly said = signal<Line[]>([]);
  protected readonly answering = signal(false);
  private readonly askedIds = signal<string[]>([]);

  /** The bar under the pointer, read out beside the strip. */
  protected readonly hovered = signal<ScanBar | null>(null);

  /** Today's price per symbol, used to re-price calls made on earlier days. */
  private readonly prices = signal<Map<string, number>>(new Map());

  private timers: ReturnType<typeof setTimeout>[] = [];
  private scanTimer?: ReturnType<typeof setInterval>;

  constructor() {
    forkJoin({
      session: this.market.getSession().pipe(catchError(() => of(null))),
      breadth: this.market.getBreadth().pipe(catchError(() => of(null))),
      // Who we are talking to, by name. Failure is non-fatal: the greeting
      // just goes without one.
      who: this.user.getAuthUser().pipe(catchError(() => of(null))),
      // null on failure, never [] — see the note on `scored`.
      //
      // The whole scored universe, not a top-20 slice. He says "I went
      // through all 86 companies" and then names the strongest buy; if the
      // ranking only ever saw the top 20 by score, both halves of that could
      // be true separately while the sentence as a whole was not. The right
      // name can sit at rank 30 precisely because the names above it are too
      // expensive to buy — which is the case he exists to catch.
      opportunities: this.stock.getOpportunities(100, 0).pipe(catchError(() => of(null))),
      // Every listed company, for the strip. Priced from lastPrice against
      // prevClose so each bar is a real move, not a decoration.
      all: this.stock.filter({ page: 1, size: 120 }).pipe(
        map((res) => res.content),
        catchError(() => of(null)),
      ),
    }).subscribe(({ session, breadth, opportunities, all, who }) => {
      this.session.set(session);
      this.profileName.set(who?.firstName ?? null);
      this.scored.set(opportunities);
      this.listedTotal.set(breadth?.total ?? null);
      this.listFailed.set(all === null);

      const stocks = all ?? [];
      const priced = new Map<string, number>();
      for (const s of stocks) {
        if (s.lastPrice !== null) priced.set(s.symbol, s.lastPrice);
      }
      this.prices.set(priced);

      // found() reads the signals set above, so it is correct by this point.
      this.bars.set(this.toBars(stocks, this.found()?.symbol ?? null));
      this.recordCall();

      this.phase.set('reading');
      this.runScan();
    });

    // The name can land after the greeting has already been spoken — a slow
    // auth round trip, a sign-in in another tab, a retry that succeeded. The
    // briefing is a frozen list of strings once composed, so without this the
    // reader keeps a nameless "Good evening." for the life of the page.
    //
    // Reads of `said` are untracked deliberately: writing a freshly mapped
    // array to a signal this effect also depended on would retrigger it
    // forever, since the new array is never reference-equal to the old one.
    effect(() => {
      const who = this.firstName();
      if (!who) return;

      untracked(() => {
        const lines = this.said();
        const hello = lines.find((l) => l.id === 'hello');
        if (!hello || hello.text.includes(who)) return;
        this.said.set(
          lines.map((l) =>
            l.id === 'hello'
              ? { ...l, text: `${this.greeting()}, ${who}. ${this.marketLine()}` }
              : l,
          ),
        );
      });
    });

    this.destroyRef.onDestroy(() => this.clearTimers());
  }

  // ── The strip ────────────────────────────────────────────────────────

  /**
   * One bar per company, scaled against the biggest mover of the day.
   *
   * <p>Relative rather than absolute, because a quiet 1% day and a violent
   * 10% one should both produce a readable strip. Companies with no price get
   * a flat grey stub rather than being dropped: "we could not price this one"
   * belongs in an honest picture of the exchange.
   */
  private toBars(stocks: StockDto[], foundSymbol: string | null): ScanBar[] {
    const raw = stocks.map((s) => {
      const pct =
        s.lastPrice !== null && s.prevClose !== null && s.prevClose !== 0
          ? ((s.lastPrice - s.prevClose) / s.prevClose) * 100
          : null;
      return { symbol: s.symbol, changePct: pct };
    });

    const biggest = Math.max(1, ...raw.map((r) => Math.abs(r.changePct ?? 0)));
    return raw.map((r) => ({
      symbol: r.symbol,
      changePct: r.changePct,
      magnitude: r.changePct === null ? 0.1 : Math.max(0.1, Math.abs(r.changePct) / biggest),
      found: !!foundSymbol && r.symbol === foundSymbol,
    }));
  }

  protected barClass(bar: ScanBar): string {
    if (bar.changePct === null) return 'flat';
    if (bar.changePct > 0) return 'up';
    if (bar.changePct < 0) return 'down';
    return 'flat';
  }

  /** A bar only lights once the sweep has passed it. */
  protected barLit(index: number): boolean {
    const total = Math.max(1, this.bars().length);
    return (index / total) * 100 <= this.scanPct();
  }

  private runScan(): void {
    if (this.reducedMotion()) {
      this.scanPct.set(100);
      this.scanned.set(this.bars().length);
      this.said.set(this.briefing());
      this.phase.set('ready');
      return;
    }

    const total = this.bars().length;
    const step = 100 / 46; // ~46 frames, about 1.2s at 26ms
    this.scanTimer = setInterval(() => {
      const next = this.scanPct() + step;
      if (next >= 100) {
        this.scanPct.set(100);
        this.scanned.set(total);
        this.stopScan();
        this.speak();
      } else {
        this.scanPct.set(next);
        this.scanned.set(Math.round((next / 100) * total));
      }
    }, 26);
  }

  // ── The briefing ─────────────────────────────────────────────────────

  private speak(): void {
    this.phase.set('speaking');
    const lines = this.briefing();
    lines.forEach((line, i) => {
      // Staggered, so the lines land like someone talking rather than a
      // paragraph appearing all at once.
      this.timers.push(
        setTimeout(() => {
          this.said.update((s) => [...s, line]);
          if (i === lines.length - 1) this.phase.set('ready');
        }, 460 * i),
      );
    });
  }

  /**
   * What the strip actually shows, counted from the strip itself.
   *
   * <p>Counted here rather than taken from /market/breadth on purpose: the
   * bars are the evidence on screen, so the spoken numbers have to be the
   * numbers a reader could count off them. Two sources would eventually
   * disagree, and the one that disagreed would be the one being read aloud.
   */
  private readonly tally = computed(() => {
    let up = 0;
    let down = 0;
    let flat = 0;
    let unpriced = 0;
    for (const bar of this.bars()) {
      if (bar.changePct === null) unpriced++;
      else if (bar.changePct > 0) up++;
      else if (bar.changePct < 0) down++;
      else flat++;
    }
    return { up, down, flat, unpriced, read: this.bars().length };
  });

  /**
   * Names actually worth recommending, strongest first.
   *
   * <p>Two independent judgements have to agree, and this used to check only
   * one of them. TunindexScorer's <em>verdict</em> says whether the business
   * is worth owning; TradifyAnalyst's <em>stance</em> says whether today's
   * price is worth paying. Filtering on stance alone let a name whose only
   * merit was sitting inside its zone get recommended over a STRONG_BUY —
   * a good price on a weak company, which is still a weak company, and
   * exactly the kind of call that costs a reader money.
   *
   * <p>The ordering is deliberate. Verdict outranks score because a
   * STRONG_BUY at 74 is a stronger recommendation than a BUY at 78; the
   * scorer already weighed that and we should not silently re-weigh it.
   * Confidence and data completeness break the remaining ties, so a
   * thinly-covered name never wins on an equal score.
   *
   * <p>Sorted here rather than trusting the order the API sent: "he
   * recommends the strongest one" is a promise this component makes, and a
   * promise resting on a remote sort is one broken silently.
   */
  private readonly liveEntries = computed(() =>
    (this.scored() ?? [])
      .filter((r) => r.tradeSetup?.stance === 'ACCUMULATE_NOW')
      .filter((r) => r.verdict === 'STRONG_BUY' || r.verdict === 'BUY')
      .sort(
        (a, b) =>
          VERDICT_RANK[b.verdict] - VERDICT_RANK[a.verdict] ||
          b.overallScore - a.overallScore ||
          (b.tradeSetup?.confidence ?? 0) - (a.tradeSetup?.confidence ?? 0) ||
          b.dataCompleteness - a.dataCompleteness,
      ),
  );

  /**
   * In their buy zone, but the business does not clear the bar.
   *
   * <p>Tracked separately so this case gets its own sentence. "Nothing is in
   * a buy zone" and "things are in a buy zone but none of them is worth
   * owning" are different market conditions, and collapsing them into one
   * message loses the more interesting of the two.
   */
  private readonly inZoneButWeak = computed(() =>
    (this.scored() ?? []).filter(
      (r) =>
        r.tradeSetup?.stance === 'ACCUMULATE_NOW' &&
        r.verdict !== 'STRONG_BUY' &&
        r.verdict !== 'BUY',
    ),
  );

  /** The highest-scoring name overall — buyable today or not. */
  private readonly topRanked = computed(() => {
    const all = [...(this.scored() ?? [])].sort((a, b) => b.overallScore - a.overallScore);
    return all[0] ?? null;
  });

  /** Names scoring above the one being recommended. */
  private readonly outscoring = computed(() => {
    const best = this.found();
    if (!best) return [];
    return (this.scored() ?? []).filter((r) => r.overallScore > best.overallScore);
  });

  /**
   * Why a higher-scoring name is not the recommendation.
   *
   * <p>Always a fact about that stock's own setup, never a generality — the
   * reader can check every one of these against the row it came from.
   */
  private whyNot(row: OpportunityScore): string {
    const s = row.tradeSetup;
    if (!s) return 'I have no entry plan on it';
    switch (s.stance) {
      case 'BUY_THE_DIP':
        return s.distanceToZonePct !== null
          ? `it is trading about ${Math.round(s.distanceToZonePct)}% above my buy zone`
          : 'it is trading above my buy zone';
      case 'WAIT_FOR_CONFIRMATION':
        return 'its turn has not confirmed yet';
      case 'HOLD_OFF':
        return 'its trend does not support buying it here';
      case 'NO_SETUP':
        return 'it has too little history for me to place a level on it';
      default:
        return 'it is not at a price I would pay';
    }
  }

  private readonly nearZone = computed(() =>
    (this.scored() ?? [])
      .filter((r) => r.tradeSetup?.stance === 'BUY_THE_DIP')
      .sort(
        (a, b) => (a.tradeSetup!.distanceToZonePct ?? 99) - (b.tradeSetup!.distanceToZonePct ?? 99),
      ),
  );

  protected readonly found = computed(() => this.liveEntries()[0] ?? null);

  // ── Memory: what he has already told you ─────────────────────────────

  /** Calendar date in Tunis, so a call is dated by the exchange's day. */
  private today(): string {
    return this.session()?.tunisTime?.slice(0, 10) ?? new Date().toISOString().slice(0, 10);
  }

  /**
   * Writes today's call into the journal.
   *
   * <p>Only the price is worth arguing about, and it is deliberately
   * `best.lastPrice` — the price the reader could actually have paid when
   * told — rather than the zone midpoint, which would flatter the record by
   * assuming a fill that may never have been available.
   */
  private recordCall(): void {
    const best = this.found();
    const setup = best?.tradeSetup;
    if (!best || !setup || best.lastPrice === null) return;

    this.journal.record({
      symbol: best.symbol,
      price: best.lastPrice,
      currency: best.currency,
      madeOn: this.today(),
      zoneLow: setup.buyZoneLow,
      zoneHigh: setup.buyZoneHigh,
      target: setup.target1,
      verdict: best.verdict,
      score: best.overallScore,
    });
  }

  /**
   * Past calls priced against today.
   *
   * <p>Calls made today are excluded. A recommendation given four minutes ago
   * showing "+0.0%" is not a track record, it is noise dressed as one.
   */
  private readonly settled = computed<SettledCall[]>(() => {
    const prices = this.prices();
    const today = this.today();

    return this.journal
      .calls()
      .filter((c) => c.madeOn < today)
      .map((c) => {
        const now = prices.get(c.symbol) ?? null;
        return {
          ...c,
          nowPrice: now,
          changePct: now !== null && c.price !== 0 ? ((now - c.price) / c.price) * 100 : null,
          daysHeld: Math.max(
            1,
            Math.round(
              (Date.parse(`${today}T00:00:00Z`) - Date.parse(`${c.madeOn}T00:00:00Z`)) / 86400000,
            ),
          ),
        };
      });
  });

  /** Past calls we can actually score. Priceless ones are excluded, not zeroed. */
  private readonly scoredCalls = computed(() =>
    this.settled().filter((c) => c.changePct !== null),
  );

  // ── Your names ───────────────────────────────────────────────────────

  /**
   * The watchlist, scored.
   *
   * <p>The single cheapest way to stop being a generic market summary: these
   * are the names the reader chose, so this is the part of the briefing that
   * is about them rather than about the exchange.
   */
  private readonly myNames = computed(() => {
    const watched = new Set(this.watchlist.symbols());
    if (watched.size === 0) return [];
    return (this.scored() ?? []).filter((r) => watched.has(r.symbol));
  });

  /** Watched names that are both recommended and at a payable price. */
  private readonly myBuyable = computed(() =>
    this.myNames().filter(
      (r) =>
        r.tradeSetup?.stance === 'ACCUMULATE_NOW' &&
        (r.verdict === 'STRONG_BUY' || r.verdict === 'BUY'),
    ),
  );

  /** Under the name, so the reader always knows what the desk is doing. */
  protected readonly status = computed(() => {
    switch (this.phase()) {
      case 'loading':
        return 'Opening the tape…';
      case 'reading':
        return 'Reading the tape…';
      case 'speaking':
        return 'Typing…';
      default:
        return this.answering() ? 'Typing…' : 'At the desk';
    }
  });

  protected readonly greeting = computed(() => {
    const tunis = this.session()?.tunisTime;
    const hour = tunis ? Number(tunis.slice(11, 13)) : new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  });

  /**
   * The reader's first name, as they gave it when they registered.
   *
   * <p>This was originally guessed from the email address, which was the
   * wrong instinct twice over. It was unreliable — "faresbenslama95" has a
   * first name in it that no safe rule extracts, so the greeting silently
   * gave up — and it was guessing at something the database already knows.
   * GET /users/auth-user returns the stored profile; a name the user typed
   * themselves is the only one worth greeting them by.
   */
  private readonly profileName = signal<string | null>(null);

  protected readonly firstName = computed(() => {
    const name = this.profileName()?.trim();
    if (!name) return null;
    // Guard the empty-ish values a profile can legitimately hold rather than
    // greeting someone as "".
    const first = name.split(/\s+/)[0] ?? '';
    return first.length >= 2 ? first : null;
  });

  /**
   * When the reading happened, Tunis time.
   *
   * <p>A briefing with no timestamp is a briefing you cannot tell is stale.
   * Taken from the exchange clock rather than the browser's, because the
   * figures are the exchange's and a reader in another timezone should see
   * the market's hour, not their own.
   */
  protected readonly readAt = computed(() => this.session()?.tunisTime?.slice(11, 16) ?? null);

  /** "+2.14%" / "−0.80%" / "no price", for the strip readout. */
  protected change(bar: ScanBar): string {
    if (bar.changePct === null) return 'no price today';
    const sign = bar.changePct > 0 ? '+' : bar.changePct < 0 ? '−' : '';
    return `${sign}${Math.abs(bar.changePct).toFixed(2)}%`;
  }

  private marketLine(): string {
    const s = this.session();
    if (!s) return 'I could not read the session clock just now.';
    switch (s.state) {
      case 'OPEN':
        return 'The BVMT is open and trading.';
      case 'PRE_OPEN':
        return 'We are in pre-opening.';
      case 'PRE_CLOSE':
        return 'The exchange is about to close.';
      case 'WEEKEND':
        return 'The exchange is shut for the weekend, so these are Friday’s closing figures.';
      default:
        return 'The exchange is closed for the day.';
    }
  }

  /** A price the way the desk would quote it, or an honest dash. */
  private price(value: number | null, currency: string | null): string {
    if (value === null) return '—';
    return currency ? `${value.toFixed(2)} ${currency}` : value.toFixed(2);
  }

  /** "7.40–8.10 TND", with the unit stated once. */
  private band(low: number | null, high: number | null, currency: string | null): string {
    if (low === null || high === null) return '—';
    return `${low.toFixed(2)}–${high.toFixed(2)}${currency ? ` ${currency}` : ''}`;
  }

  /**
   * What the desk says on arrival.
   *
   * <p>Every sentence below has to be defensible against the payloads that
   * produced it, which mostly means being careful about failure. When a call
   * did not come back, the line says so; it never converts a missing answer
   * into a confident negative. "There is nothing to buy today" and "I could
   * not check what there is to buy today" are different statements, and a
   * reader acts differently on each.
   */
  private briefing(): Line[] {
    const t = this.tally();
    const listed = this.listedTotal();
    const live = this.liveEntries().length;
    const near = this.nearZone().length;

    const who = this.firstName();
    const lines: Line[] = [
      {
        id: 'hello',
        from: 'analyst',
        text: `${this.greeting()}${who ? `, ${who}` : ''}. ${this.marketLine()}`,
      },
    ];

    // Before today's view: how the last thing he said has actually gone. An
    // analyst who never revisits a call is one you have no reason to believe
    // on the next one.
    const history = this.scoredCalls();
    if (history.length > 0) {
      const last = history[0];
      const move = last.changePct!;
      const days = last.daysHeld === 1 ? 'a day' : `${last.daysHeld} days`;
      lines.push({
        id: 'track',
        from: 'analyst',
        text: `Last time we spoke I flagged ${last.symbol} at ${this.price(last.price, last.currency)}. It is ${this.price(last.nowPrice, last.currency)} now — ${move >= 0 ? 'up' : 'down'} ${Math.abs(move).toFixed(1)}% in ${days}.`,
      });
    }

    if (this.listFailed() || t.read === 0) {
      lines.push({
        id: 'scan',
        from: 'analyst',
        text: 'I could not pull the company list just now, so I have not read the market today.',
      });
    } else {
      // Only claim "all" when breadth agrees the list is complete; otherwise
      // quote the coverage honestly as "N of M".
      const coverage =
        listed !== null && listed > t.read
          ? `${t.read} of the ${listed} listed companies`
          : `all ${t.read} listed companies`;

      let text = `I have just been through ${coverage} — ${t.up} advanced, ${t.down} fell`;
      text += t.flat > 0 ? `, ${t.flat} finished level.` : '.';
      if (t.unpriced > 0) {
        text += ` ${t.unpriced} had no price today, so ${t.unpriced === 1 ? 'it is' : 'they are'} not in that count.`;
      }
      lines.push({ id: 'scan', from: 'analyst', text });
    }

    // The scoring desk being unreachable is its own answer, and not a quiet one.
    if (this.scored() === null) {
      lines.push({
        id: 'verdict',
        from: 'analyst',
        emphasis: true,
        text: 'I could not reach the scoring desk just now, so I have no call for you — I would rather say that than guess at one.',
      });
      return lines;
    }

    if (this.context() === 'opportunities') {
      lines.push({
        id: 'verdict',
        from: 'analyst',
        emphasis: true,
        text:
          live > 0
            ? `${live === 1 ? 'One name is' : `${live} names are`} inside the buy zone right now. The rest are good businesses at prices I would not pay yet.`
            : near > 0
              ? `Nothing is at a price I would buy today, but ${near === 1 ? 'one is' : `${near} are`} close. Ask me and I will give you the levels.`
              : 'Nothing here is near an entry today — that is an answer, not a gap.',
      });
      return lines;
    }

    const best = this.found();
    if (best?.tradeSetup) {
      const s = best.tradeSetup;
      const zone = this.band(s.buyZoneLow, s.buyZoneHigh, best.currency);

      // Deal with the higher-scoring names before naming the pick, because
      // the reader can see them at the top of the table and will otherwise
      // conclude the recommendation is simply wrong. Passing over the best
      // score IS the judgement being sold here — it only works if it is
      // stated out loud, with the reason attached.
      const above = this.outscoring();
      const top = this.topRanked();
      if (above.length > 0 && top) {
        const others =
          above.length > 1
            ? ` ${above.length - 1} other${above.length > 2 ? 's' : ''} score above it too, for much the same reason.`
            : '';
        lines.push({
          id: 'passed-over',
          from: 'analyst',
          text: `${top.symbol} scores highest today at ${top.overallScore}, but ${this.whyNot(top)} — so it is not the one I would put money into this morning.${others}`,
        });
      }
      // target1 is genuinely nullable, and "a first target of null" is the
      // kind of sentence that destroys trust in everything around it.
      const target =
        s.target1 !== null
          ? ` First target ${this.price(s.target1, best.currency)}.`
          : ' I do not have a clean first target on it yet.';
      lines.push({
        id: 'verdict',
        from: 'analyst',
        emphasis: true,
        // The verdict is stated alongside the score, because they are not the
        // same claim and the reader is entitled to both.
        text: `${best.symbol} is the one: ${VERDICT_LABELS[best.verdict].toLowerCase()} on my numbers at ${best.overallScore}/100, and trading inside its buy zone at ${zone}.${target}`,
      });
    } else if (this.inZoneButWeak().length > 0) {
      const n = this.inZoneButWeak().length;
      lines.push({
        id: 'verdict',
        from: 'analyst',
        emphasis: true,
        text:
          n === 1
            ? 'One name is sitting in a buy zone today, but it does not clear my bar as a business — and a good price on a weak company is still a weak company.'
            : `${n} names are sitting in a buy zone today, but none of them clears my bar as a business — and a good price on a weak company is still a weak company.`,
      });
    } else if (near > 0) {
      const n = this.nearZone()[0];
      const gap = n.tradeSetup!.distanceToZonePct;
      const how = gap !== null ? ` — about ${Math.round(gap)}% above where I would step in` : '';
      lines.push({
        id: 'verdict',
        from: 'analyst',
        emphasis: true,
        text: `Nothing is worth buying at today’s prices. ${n.symbol} is the closest${how}.`,
      });
    } else {
      lines.push({
        id: 'verdict',
        from: 'analyst',
        emphasis: true,
        text: 'I checked every name I score and none of them is worth acting on today. I would rather tell you that than invent something.',
      });
    }

    // Finally, the reader's own names. Last because it is the part they will
    // come back for, and it should be what the briefing leaves them on.
    const mine = this.myNames();
    if (mine.length > 0) {
      const buyable = this.myBuyable();
      const count = `${mine.length} name${mine.length === 1 ? '' : 's'}`;
      lines.push({
        id: 'watchlist',
        from: 'analyst',
        text:
          buyable.length > 0
            ? `On your own list: you are watching ${count}, and ${buyable.map((b) => b.symbol).join(' and ')} ${buyable.length === 1 ? 'is' : 'are'} at a price I would pay today.`
            : `On your own list: you are watching ${count}, and none of them is at a price I would pay today.`,
      });
    }

    return lines;
  }

  // ── Questions back ───────────────────────────────────────────────────

  /**
   * What the reader can ask next.
   *
   * <p>Offered only when there is a real answer behind them. A question chip
   * that returns "no information available" is worse than not offering it.
   */
  protected readonly followUps = computed<FollowUp[]>(() => {
    if (this.phase() !== 'ready') return [];
    const asked = this.askedIds();
    const out: FollowUp[] = [];
    const best = this.found();

    if (best) out.push({ id: 'why', label: `Why ${best.symbol}?` });

    // The obvious objection, offered as a question rather than waiting to be
    // raised: the reader can see a higher score at the top of the table.
    const top = this.topRanked();
    if (best && top && top.symbol !== best.symbol && top.overallScore > best.overallScore) {
      out.push({ id: 'nottop', label: `Why not ${top.symbol}?` });
    }

    if (this.nearZone().length > 0) out.push({ id: 'waiting', label: 'What are you waiting for?' });
    if (best?.tradeSetup?.risks?.length || best?.warnings?.length) {
      out.push({ id: 'risk', label: 'Anything worry you?' });
    }
    if (this.myNames().length > 0) out.push({ id: 'mine', label: 'What about my watchlist?' });
    if (this.scoredCalls().length > 0) {
      out.push({ id: 'record', label: 'How have your calls done?' });
    }
    out.push({ id: 'how', label: 'How do you decide?' });

    return out.filter((f) => !asked.includes(f.id));
  });

  protected ask(followUp: FollowUp): void {
    if (this.answering()) return;
    this.askedIds.update((a) => [...a, followUp.id]);
    this.said.update((s) => [...s, { id: `q-${followUp.id}`, from: 'you', text: followUp.label }]);
    this.answering.set(true);

    // A beat before the reply. An instant answer reads as a lookup table; a
    // short pause reads as someone considering the question.
    this.timers.push(
      setTimeout(
        () => {
          this.said.update((s) => [
            ...s,
            { id: `a-${followUp.id}`, from: 'analyst', text: this.answer(followUp.id) },
          ]);
          this.answering.set(false);
        },
        this.reducedMotion() ? 0 : 700,
      ),
    );
  }

  /** Answers assembled from the payloads already loaded. */
  private answer(id: string): string {
    const best = this.found();

    switch (id) {
      case 'why': {
        if (!best?.tradeSetup) return 'I have no live entry to explain right now.';
        const s = best.tradeSetup;
        const why =
          s.evidence?.slice(0, 2).join('; ') || s.regimeSummary || 'the structure turned up';
        return `${best.symbol} scores ${best.overallScore} out of 100, and the turn is confirmed — ${why}. That is why the zone is live rather than something to watch.`;
      }
      case 'record': {
        const past = this.scoredCalls();
        if (past.length === 0) return 'I have not made a call here before today.';

        const up = past.filter((c) => c.changePct! > 0).length;
        const down = past.filter((c) => c.changePct! < 0).length;
        const ranked = [...past].sort((a, b) => b.changePct! - a.changePct!);
        const top = ranked[0];
        const bottom = ranked[ranked.length - 1];

        const fmt = (c: SettledCall) =>
          `${c.symbol} ${c.changePct! >= 0 ? '+' : '−'}${Math.abs(c.changePct!).toFixed(1)}%`;

        // The extremes are only worth quoting when there are two of them;
        // with a single call, "best and worst" is the same call twice.
        const spread =
          past.length > 1 ? ` Best ${fmt(top)}, worst ${fmt(bottom)}.` : ` That one is ${fmt(top)}.`;

        // The caveat is not modesty, it is accuracy — and an analyst who
        // oversells a handful of calls as a track record has told you
        // something about every other number they quote.
        return `${past.length} call${past.length === 1 ? '' : 's'} I can price so far: ${up} up, ${down} down.${spread} That is every call I have made to you, not a selection of the good ones — though it is a short record kept in this browser, so read it as a log rather than a verified track record.`;
      }
      case 'mine': {
        const mine = this.myNames();
        if (mine.length === 0) return 'You are not watching anything yet.';
        const list = mine
          .slice(0, 6)
          .map((r) => {
            const s = r.tradeSetup;
            if (!s) return `${r.symbol}: no entry plan`;
            const where = s.priceInBuyZone
              ? `in the zone at ${this.band(s.buyZoneLow, s.buyZoneHigh, r.currency)}`
              : s.distanceToZonePct !== null
                ? `${Math.round(s.distanceToZonePct)}% above my zone`
                : 'not near an entry';
            return `${r.symbol} — ${STANCE_LABELS[s.stance].toLowerCase()}, ${where}`;
          })
          .join('; ');
        const more = mine.length > 6 ? ` …and ${mine.length - 6} more.` : '';
        return `Here is where your list stands: ${list}.${more}`;
      }
      case 'nottop': {
        const top = this.topRanked();
        if (!top || !best) return 'Nothing outscores what I picked today.';
        const s = top.tradeSetup;
        const level =
          s?.buyZoneLow != null && s?.buyZoneHigh != null
            ? ` It trades at ${this.price(top.lastPrice, top.currency)} against a zone of ${this.band(s.buyZoneLow, s.buyZoneHigh, top.currency)}.`
            : '';
        return `${top.symbol} scores ${top.overallScore} to ${best.symbol}'s ${best.overallScore}, and on the business alone it is the better company — ${this.whyNot(top)}.${level} I would rather own the second-best company at the right price than the best one at the wrong price, because what you pay is the part of the return you control. If ${top.symbol} comes back into the zone, it becomes the call.`;
      }
      case 'waiting': {
        const near = this.nearZone().slice(0, 3);
        if (near.length === 0) return 'Nothing is close enough to be worth naming yet.';
        const list = near
          .map((n) => {
            const s = n.tradeSetup!;
            const gap = s.distanceToZonePct;
            const away = gap !== null ? ` (${Math.round(gap)}% away)` : '';
            return `${n.symbol} at ${this.band(s.buyZoneLow, s.buyZoneHigh, n.currency)}${away}`;
          })
          .join(', ');
        return `These are the levels I want before I would buy: ${list}. I will not pay more than that for them.`;
      }
      case 'risk': {
        const risks = [...(best?.tradeSetup?.risks ?? []), ...(best?.warnings ?? [])];
        if (risks.length === 0) return 'Nothing beyond the usual — but no setup is free of risk.';
        return `Yes — ${risks.slice(0, 2).join('; ')}. Worth sizing the position with that in mind.`;
      }
      default:
        return 'Valuation and timing carry equal weight, then financial health, income, momentum and news. A cheap company whose chart is still falling is not a buy — the price has to be right and the decline has to have ended. That second half is the part most screens skip.';
    }
  }

  // ── Plumbing ─────────────────────────────────────────────────────────

  /** Skips straight to the end; nobody should have to wait on an animation. */
  protected skip(): void {
    if (this.phase() === 'ready') return;
    this.clearTimers();
    this.scanPct.set(100);
    this.scanned.set(this.bars().length);
    this.said.set(this.briefing());
    this.phase.set('ready');
  }

  private reducedMotion(): boolean {
    return (
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
    );
  }

  private stopScan(): void {
    if (this.scanTimer) {
      clearInterval(this.scanTimer);
      this.scanTimer = undefined;
    }
  }

  private clearTimers(): void {
    this.stopScan();
    this.timers.forEach(clearTimeout);
    this.timers = [];
  }

  ngOnDestroy(): void {
    this.clearTimers();
  }
}

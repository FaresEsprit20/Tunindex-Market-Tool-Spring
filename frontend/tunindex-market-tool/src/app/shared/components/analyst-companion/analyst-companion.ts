import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnDestroy,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { Market } from '../../../core/services/market';
import { Stock } from '../../../core/services/stock';
import { MarketSession } from '../../../core/models/market.model';
import { StockDto } from '../../../core/models/stock.model';
import { OpportunityScore } from '../../../core/models/opportunity.model';

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
  imports: [],
  templateUrl: './analyst-companion.html',
  styleUrl: './analyst-companion.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalystCompanion implements OnDestroy {
  private readonly market = inject(Market);
  private readonly stock = inject(Stock);
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

  private timers: ReturnType<typeof setTimeout>[] = [];
  private scanTimer?: ReturnType<typeof setInterval>;

  constructor() {
    forkJoin({
      session: this.market.getSession().pipe(catchError(() => of(null))),
      breadth: this.market.getBreadth().pipe(catchError(() => of(null))),
      // null on failure, never [] — see the note on `scored`.
      opportunities: this.stock.getOpportunities(20, 0).pipe(catchError(() => of(null))),
      // Every listed company, for the strip. Priced from lastPrice against
      // prevClose so each bar is a real move, not a decoration.
      all: this.stock.filter({ page: 1, size: 120 }).pipe(
        map((res) => res.content),
        catchError(() => of(null)),
      ),
    }).subscribe(({ session, breadth, opportunities, all }) => {
      this.session.set(session);
      this.scored.set(opportunities);
      this.listedTotal.set(breadth?.total ?? null);
      this.listFailed.set(all === null);

      const stocks = all ?? [];
      const live = opportunities?.find((o) => o.tradeSetup?.stance === 'ACCUMULATE_NOW');
      this.bars.set(this.toBars(stocks, live?.symbol ?? null));

      this.phase.set('reading');
      this.runScan();
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

  protected barTitle(bar: ScanBar): string {
    if (bar.changePct === null) return `${bar.symbol}: not priced today`;
    return `${bar.symbol}: ${bar.changePct >= 0 ? '+' : ''}${bar.changePct.toFixed(2)}%`;
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

  private readonly liveEntries = computed(() =>
    (this.scored() ?? []).filter((r) => r.tradeSetup?.stance === 'ACCUMULATE_NOW'),
  );

  private readonly nearZone = computed(() =>
    (this.scored() ?? [])
      .filter((r) => r.tradeSetup?.stance === 'BUY_THE_DIP')
      .sort(
        (a, b) => (a.tradeSetup!.distanceToZonePct ?? 99) - (b.tradeSetup!.distanceToZonePct ?? 99),
      ),
  );

  protected readonly found = computed(() => this.liveEntries()[0] ?? null);

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

    const lines: Line[] = [
      { id: 'hello', from: 'analyst', text: `${this.greeting()}. ${this.marketLine()}` },
    ];

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
      // target1 is genuinely nullable, and "a first target of null" is the
      // kind of sentence that destroys trust in everything around it.
      const target =
        s.target1 !== null
          ? ` against a first target of ${this.price(s.target1, best.currency)}.`
          : '. I do not have a clean first target on it yet.';
      lines.push({
        id: 'verdict',
        from: 'analyst',
        emphasis: true,
        text: `${best.symbol} is trading inside its buy zone — ${zone}${target}`,
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
    if (this.nearZone().length > 0) out.push({ id: 'waiting', label: 'What are you waiting for?' });
    if (best?.tradeSetup?.risks?.length || best?.warnings?.length) {
      out.push({ id: 'risk', label: 'Anything worry you?' });
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

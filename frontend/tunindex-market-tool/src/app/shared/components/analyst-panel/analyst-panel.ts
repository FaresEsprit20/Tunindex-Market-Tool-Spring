import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import {
  PHASE_LABELS,
  PHASE_SEQUENCE,
  Phase,
  STANCE_BLURBS,
  STANCE_LABELS,
  STANCE_TONE,
  TradeSetup,
} from '../../../core/models/trade-setup.model';

/**
 * The analyst's call on one stock: what to do, at what price, and why.
 *
 * <p>Built around the order the reader actually needs it in - the instruction
 * first, the levels second, the reasoning underneath. A panel that opens with
 * "RSI 28.5" makes the reader do the analyst's job; one that opens with "Buy
 * now, 3.20 - 3.48" answers the question they came with and keeps the workings
 * available for anyone who wants to check them.
 *
 * <p>Only one stance is a green light, and the colour treatment says so. If
 * every state looked encouraging the panel would be decoration rather than
 * advice.
 */
@Component({
  selector: 'app-analyst-panel',
  imports: [DecimalPipe],
  templateUrl: './analyst-panel.html',
  styleUrl: './analyst-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalystPanel {
  readonly setup = input.required<TradeSetup>();
  readonly currency = input<string | null>('TND');

  /** Compact form for the opportunities list, where space is tight. */
  readonly compact = input(false);

  protected readonly stanceLabels = STANCE_LABELS;
  protected readonly stanceBlurbs = STANCE_BLURBS;
  protected readonly phaseLabels = PHASE_LABELS;
  protected readonly phases = PHASE_SEQUENCE;

  protected readonly tone = computed(() => STANCE_TONE[this.setup().stance]);

  protected readonly hasZone = computed(() => {
    const s = this.setup();
    return s.buyZoneLow !== null && s.buyZoneHigh !== null;
  });

  /** Index along the cycle track, or -1 when there is no structure to place. */
  protected readonly phaseIndex = computed(() => {
    const phase = this.setup().phase;
    return phase ? PHASE_SEQUENCE.indexOf(phase as Phase) : -1;
  });

  /**
   * Where today's price sits across the whole plan, as a percentage.
   *
   * <p>Drives a single bar running stop → buy zone → target, which is the one
   * picture that makes the plan legible at a glance: how far the price has to
   * fall to be worth buying, and how far it can run afterwards.
   */
  protected readonly priceMarkerPct = computed(() => {
    const s = this.setup();
    const low = s.stopLevel ?? s.buyZoneLow;
    const high = s.target1 ?? s.buyZoneHigh;
    const price = this.currentPrice();
    if (low === null || high === null || price === null || high <= low) {
      return null;
    }
    return Math.min(100, Math.max(0, ((price - low) / (high - low)) * 100));
  });

  protected readonly zoneStartPct = computed(() => this.pctAlong(this.setup().buyZoneLow));
  protected readonly zoneEndPct = computed(() => this.pctAlong(this.setup().buyZoneHigh));

  /**
   * Today's price, inferred from the zone and the distance to it.
   *
   * <p>The setup does not carry the live price - it carries the levels and how
   * far away the price is - so this reconstructs it rather than taking a second
   * input that could disagree with the one the levels were computed from.
   */
  private readonly currentPrice = computed(() => {
    const s = this.setup();
    if (s.priceInBuyZone && s.buyZoneLow !== null && s.buyZoneHigh !== null) {
      return (s.buyZoneLow + s.buyZoneHigh) / 2;
    }
    if (s.distanceToZonePct !== null && s.buyZoneHigh !== null) {
      // distance is measured as a share of the price, so undo that.
      return s.buyZoneHigh / (1 - s.distanceToZonePct / 100);
    }
    return null;
  });

  private pctAlong(value: number | null): number | null {
    const s = this.setup();
    const low = s.stopLevel ?? s.buyZoneLow;
    const high = s.target1 ?? s.buyZoneHigh;
    if (value === null || low === null || high === null || high <= low) {
      return null;
    }
    return Math.min(100, Math.max(0, ((value - low) / (high - low)) * 100));
  }
}

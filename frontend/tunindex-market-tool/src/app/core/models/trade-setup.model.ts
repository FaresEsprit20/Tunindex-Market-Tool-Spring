/** What the Tradify Analyst says to do about a stock, and at what price. */
export type Stance =
  | 'ACCUMULATE_NOW'
  | 'BUY_THE_DIP'
  | 'WAIT_FOR_CONFIRMATION'
  | 'HOLD_OFF'
  | 'NO_SETUP';

/** Where a stock sits in its cycle. */
export type Phase =
  | 'DOWNTREND'
  | 'BOTTOMING'
  | 'REVERSING'
  | 'UPTREND'
  | 'TOPPING'
  | 'NEUTRAL';

export interface TradeSetup {
  symbol: string;
  stance: Stance;
  headline: string;

  /** The entry band. Null when there is no defensible level. */
  buyZoneLow: number | null;
  buyZoneHigh: number | null;
  priceInBuyZone: boolean;
  distanceToZonePct: number | null;
  buyZoneBasis: string | null;

  target1: number | null;
  target2: number | null;
  stopLevel: number | null;
  expectedReturnPct: number | null;
  riskReward: number | null;

  phase: Phase | null;
  regimeSummary: string | null;
  evidence: string[];
  risks: string[];
  confidence: number;
}

/**
 * How each stance reads to someone deciding what to do this morning.
 *
 * <p>Written as instructions rather than labels. "REVERSING" is a state; "Buy
 * now — the price is in the zone" is something a reader can act on, which is
 * the entire difference the analyst is meant to make.
 */
export const STANCE_LABELS: Record<Stance, string> = {
  ACCUMULATE_NOW: 'Buy now',
  BUY_THE_DIP: 'Wait for the dip',
  WAIT_FOR_CONFIRMATION: 'Watch closely',
  HOLD_OFF: 'Stay out',
  NO_SETUP: 'No read',
};

/** Short second line explaining the call. */
export const STANCE_BLURBS: Record<Stance, string> = {
  ACCUMULATE_NOW: 'The turn is confirmed and the price is still in range',
  BUY_THE_DIP: 'Worth owning, but not at this price',
  WAIT_FOR_CONFIRMATION: 'Basing, but the turn has not confirmed yet',
  HOLD_OFF: 'The trend does not support buying here',
  NO_SETUP: 'Not enough trading history to call it',
};

/** Drives the colour treatment — only the first is a green light. */
export const STANCE_TONE: Record<Stance, 'go' | 'ready' | 'watch' | 'stop' | 'muted'> = {
  ACCUMULATE_NOW: 'go',
  BUY_THE_DIP: 'ready',
  WAIT_FOR_CONFIRMATION: 'watch',
  HOLD_OFF: 'stop',
  NO_SETUP: 'muted',
};

export const PHASE_LABELS: Record<Phase, string> = {
  DOWNTREND: 'Falling',
  BOTTOMING: 'Basing',
  REVERSING: 'Turning up',
  UPTREND: 'Advancing',
  TOPPING: 'Extended',
  NEUTRAL: 'No structure',
};

/**
 * The cycle in order, so the UI can show where a stock sits along it.
 *
 * <p>A position on a track says more than a word does: "Basing" alone means
 * little, while seeing it one step before "Turning up" shows the reader what
 * they are waiting for.
 */
export const PHASE_SEQUENCE: Phase[] = ['DOWNTREND', 'BOTTOMING', 'REVERSING', 'UPTREND', 'TOPPING'];

/**
 * A call the Tradify Analyst made, as it stood the day it was made.
 *
 * <p>Every field is a snapshot, never a live reference. The point of the
 * journal is to be able to say "I told you 7.60 on 12 September" and have it
 * still be 7.60 afterwards — a record that silently re-reads today's price is
 * not a record, it is a mirror.
 */
export interface AnalystCall {
  symbol: string;
  /** The traded price at the moment of the call. What it is judged against. */
  price: number;
  currency: string | null;
  /** Calendar date in Tunis, yyyy-mm-dd. */
  madeOn: string;
  /** The entry band quoted at the time. */
  zoneLow: number | null;
  zoneHigh: number | null;
  target: number | null;
  /** The scorer's call and score on the day, for context when reviewing. */
  verdict: string;
  score: number;
}

/** A past call priced against today. */
export interface SettledCall extends AnalystCall {
  /** Null when the stock has no current price — reported, never guessed. */
  nowPrice: number | null;
  changePct: number | null;
  daysHeld: number;
}

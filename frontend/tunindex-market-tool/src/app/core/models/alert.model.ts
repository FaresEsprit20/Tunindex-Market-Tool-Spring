// Alert rules and the notifications they produce.

export type AlertTypeKey =
  | 'PRICE_ABOVE'
  | 'PRICE_BELOW'
  | 'DAY_MOVE_EXCEEDS'
  | 'SCORE_ABOVE'
  | 'SCORE_BELOW'
  | 'VERDICT_CHANGE'
  | 'NEGATIVE_NEWS'
  | 'NEAR_52W_LOW';

export interface AlertTypeOption {
  type: AlertTypeKey;
  description: string;
  /** Event-style types (verdict change, negative news) take no threshold. */
  requiresThreshold: boolean;
}

export interface AlertRule {
  id: number;
  symbol: string;
  type: AlertTypeKey;
  typeDescription: string;
  threshold: number | null;
  enabled: boolean;
  lastTriggeredAt: string | null;
  createdAt: string;
}

export interface CreateAlertRule {
  symbol: string;
  type: AlertTypeKey;
  threshold?: number | null;
}

export interface AppNotification {
  id: number;
  title: string;
  body: string;
  category: string;
  tone: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
  symbol: string | null;
  read: boolean;
  createdAt: string;
}

/** Units the threshold input should show, per alert type. */
export const THRESHOLD_UNITS: Record<AlertTypeKey, string> = {
  PRICE_ABOVE: 'TND',
  PRICE_BELOW: 'TND',
  DAY_MOVE_EXCEEDS: '%',
  SCORE_ABOVE: '/100',
  SCORE_BELOW: '/100',
  NEAR_52W_LOW: '% of range',
  VERDICT_CHANGE: '',
  NEGATIVE_NEWS: '',
};

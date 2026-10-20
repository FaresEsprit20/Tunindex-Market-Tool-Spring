/** Mirrors the ad module's enums. Kept in step with the backend by /ads/options. */
export type AdType =
  | 'VIDEO_PREROLL'
  | 'VIDEO_MIDROLL'
  | 'VIDEO_POSTROLL'
  | 'BANNER'
  | 'INTERSTITIAL'
  | 'NATIVE'
  | 'REWARDED'
  | 'DISPLAY'
  | 'SPONSORED_CONTENT';

export type AdSource =
  | 'GOOGLE_ADSENSE'
  | 'GOOGLE_AD_MANAGER'
  | 'YOUTUBE'
  | 'PROGRAMMATIC_EXCHANGE'
  | 'DIRECT_ADVERTISER'
  | 'AFFILIATE'
  | 'HOUSE';

export type AdPlacement =
  | 'DASHBOARD_TOP'
  | 'DASHBOARD_INLINE'
  | 'STOCK_LIST_INLINE'
  | 'STOCK_DETAIL_SIDEBAR'
  | 'PORTFOLIO_SIDEBAR'
  | 'ANALYSIS_PRE_CONTENT'
  | 'NEWS_FEED_INLINE'
  | 'GLOBAL_FOOTER'
  | 'INTERSTITIAL_ON_NAVIGATION';

export type AdEventType =
  | 'IMPRESSION'
  | 'COMPLETED_VIEW'
  | 'SKIPPED'
  | 'CLICK'
  | 'CONVERSION';

export interface Advertisement {
  id: number;
  name: string;
  advertiser: string | null;
  type: AdType;
  source: AdSource;
  placement: AdPlacement;
  creativeUrl: string | null;
  targetUrl: string | null;
  /** The provider's own slot id, for a network-rendered unit. */
  externalUnitId: string | null;
  altText: string | null;
  skippable: boolean;
  skipAfterSeconds: number | null;
  durationSeconds: number | null;
}

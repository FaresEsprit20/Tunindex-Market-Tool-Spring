/**
 * Development settings. Replaced at build time by environment.prod.ts for a
 * production build — see fileReplacements in angular.json.
 *
 * <p>These values used to be hard-coded constants in api.config.ts, which
 * meant a production bundle pointed every request at the developer's own
 * laptop. Nothing would have failed at build time; the deployed site would
 * simply have called localhost and shown no data at all.
 */
export const environment = {
  production: false,

  /** The gateway. 8070 rather than 8080 — another local project holds 8080. */
  gatewayUrl: 'http://localhost:8070',

  /**
   * AdSense publisher id, e.g. "ca-pub-1234567890123456".
   *
   * <p>Deliberately empty in development. AdSense will not serve ads to
   * localhost anyway, and loading its script here would put test impressions
   * against a real account — which is a policy violation, not just noise.
   */
  adsensePublisherId: '',

  /** Google Ad Manager network code, for video inventory. Empty disables it. */
  adManagerNetworkCode: '',

  /** reCAPTCHA v3 site key. Public by design. */
  recaptchaSiteKey: '6LeiiU0rAAAAAKs_QaJjbyQnFAznRuacFNNTZkdW',
};

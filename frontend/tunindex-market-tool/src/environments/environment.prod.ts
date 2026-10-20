/**
 * Production settings, swapped in for environment.ts at build time.
 *
 * <p>Every value below has to be filled in before a real deploy. They are left
 * as obvious placeholders rather than plausible-looking defaults on purpose: a
 * wrong-but-reasonable URL here produces a site that loads and then silently
 * shows nothing, which is far harder to diagnose than one that fails loudly.
 */
export const environment = {
  production: true,

  /**
   * Where the gateway is reachable from a visitor's browser.
   *
   * <p>Must be HTTPS in production. The session cookies are issued with
   * SameSite=Lax over a cross-origin request, and browsers refuse to store a
   * cookie from an insecure origin on a secure page — so a plain-http gateway
   * behind an https site means nobody can stay signed in.
   *
   * <p>Simplest arrangement is to serve the app and the gateway from the same
   * hostname behind one reverse proxy, e.g. https://tunindex.tn with /api
   * proxied to the gateway. Then this is just "https://tunindex.tn" and the
   * requests are same-origin, which removes CORS from the picture entirely.
   */
  gatewayUrl: 'https://REPLACE-WITH-YOUR-DOMAIN',

  /**
   * AdSense publisher id from https://adsense.google.com once approved.
   *
   * <p>Format: ca-pub- followed by 16 digits. Public — it appears in the page
   * source of every AdSense site, so it belongs here and not in a secret
   * store. While it is empty no AdSense script loads at all.
   */
  adsensePublisherId: '',

  /** Ad Manager network code, if you use GAM for video. Empty disables it. */
  adManagerNetworkCode: '',

  /**
   * reCAPTCHA site key for the production domain.
   *
   * <p>A key is registered against specific hostnames. The development key
   * below is registered for localhost and will reject tokens from a real
   * domain, so this needs its own key from the reCAPTCHA admin console.
   */
  recaptchaSiteKey: '',
};

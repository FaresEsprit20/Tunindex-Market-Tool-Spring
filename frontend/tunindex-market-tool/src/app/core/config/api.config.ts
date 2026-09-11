/**
 * Every backend call goes through the API gateway on port 8080.
 *
 * <p>The path is unchanged - the gateway forwards
 * /tunindex/market/tool/v1/stocks/** to the api service verbatim - so only
 * the port moved. Rewriting the path at the gateway instead would have meant
 * touching every service that already answers on it.
 *
 * <p>Pointing at the gateway rather than a service directly is what keeps this
 * a single constant as the platform grows: the ad module listens on its own
 * port, and without the gateway using it from here would need a second base
 * URL and a second CORS origin to keep in step with this one.
 */
export const API_BASE_URL = 'http://localhost:8080/tunindex/market/tool/v1/stocks';

/**
 * The ads API, also through the gateway.
 *
 * <p>A different path root rather than a different host, which is the point:
 * same origin, so one CORS configuration covers both and the browser sends
 * session cookies to each without further setup.
 */
export const ADS_BASE_URL = 'http://localhost:8080/api/v1/ads';

/**
 * Google reCAPTCHA v3 site key.
 *
 * <p>Public by design - it is embedded in the page and readable by anyone.
 * The secret half lives on the recaptcha service and never reaches the
 * browser, which is what makes a token forgeable only by Google.
 *
 * <p>Matches recaptcha.site-key in the api service's configuration. The two
 * must refer to the same reCAPTCHA project or every token will be rejected as
 * belonging to a different site.
 */
export const RECAPTCHA_SITE_KEY = '6LeiiU0rAAAAAKs_QaJjbyQnFAznRuacFNNTZkdW';

/**
 * Calls that carry a reCAPTCHA token.
 *
 * <p>Deliberately a short list rather than "every write". These are the
 * endpoints a bot actually targets - credential stuffing, mass sign-ups,
 * password-reset flooding. Requiring a token on ordinary in-app actions
 * (placing a paper trade, starring a stock) would spend a reCAPTCHA call on
 * every click and break each of them the moment Google is unreachable, for no
 * security gain: those endpoints already require a session.
 */
export const RECAPTCHA_PROTECTED_PATHS: readonly string[] = [
  '/auth/authenticate',
  '/auth/two-factor/verify',
  '/users/create',
  '/accounts/management/user/create',
  '/password-reset',
];

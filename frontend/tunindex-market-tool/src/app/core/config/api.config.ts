import { environment } from '../../../environments/environment';

/**
 * Every backend call goes through the API gateway.
 *
 * <p>Derived from the environment rather than hard-coded. These were literal
 * "http://localhost:8070/..." strings, which built a production bundle that
 * asked the visitor's own machine for market data - a failure with no error
 * message anywhere, just an app that loads and stays empty.
 *
 * <p>The path is unchanged: the gateway forwards
 * /tunindex/market/tool/v1/stocks/** to the api service verbatim, so only the
 * origin varies between environments.
 */
export const API_BASE_URL = `${environment.gatewayUrl}/tunindex/market/tool/v1/stocks`;

/**
 * The ads API, also through the gateway.
 *
 * <p>A different path root rather than a different host, which is the point:
 * same origin, so one CORS configuration covers both and the browser sends
 * session cookies to each without further setup.
 */
export const ADS_BASE_URL = `${environment.gatewayUrl}/api/v1/ads`;

/**
 * Google reCAPTCHA v3 site key.
 *
 * <p>Public by design - it is embedded in the page and readable by anyone. The
 * secret half lives on the recaptcha service and never reaches the browser,
 * which is what makes a token forgeable only by Google.
 *
 * <p>Keys are registered per hostname, so development and production need
 * different ones; both come from the environment for that reason.
 */
export const RECAPTCHA_SITE_KEY = environment.recaptchaSiteKey;

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

/**
 * Google AdSense publisher id, e.g. "ca-pub-1234567890123456".
 *
 * <p>Public by design - it is visible in the page source of every AdSense
 * site. The secret half of the relationship is the account itself.
 *
 * <p>Empty until a real approved account exists. While it is empty no AdSense
 * script is loaded at all, so an unconfigured build does not ship a
 * third-party tag to every visitor for nothing - and, more importantly, does
 * not register test impressions against a live account from localhost, which
 * is a policy violation rather than merely untidy.
 */
export const ADSENSE_PUBLISHER_ID = environment.adsensePublisherId;

/**
 * Google Ad Manager network code, for publishers using GAM rather than plain
 * AdSense. Empty disables the GPT integration the same way.
 */
export const AD_MANAGER_NETWORK_CODE = environment.adManagerNetworkCode;

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

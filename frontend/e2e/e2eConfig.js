/**
 * Where the end-to-end tests find the app; override with environment variables.
 *
 * - E2E_API_URL: the backend (default http://localhost:8080).
 * - E2E_APP_PORT: the port of the dev server the tests start (default 5174, next to the usual 5173).
 * - E2E_PROXY_PORT: the port of the latency proxy in front of the backend's STOMP endpoint (default 18080).
 */
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080';
export const E2E_APP_PORT = Number(process.env.E2E_APP_PORT ?? 5174);
export const PROXY_PORT = Number(process.env.E2E_PROXY_PORT ?? 18080);

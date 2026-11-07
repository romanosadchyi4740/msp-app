import { defineConfig } from '@playwright/test';
import { API_URL, E2E_APP_PORT, PROXY_PORT } from './e2e/e2eConfig.js';

/**
 * End-to-end tests (KAN-44): `npm run test:e2e`. They need the backend running (`./mvnw spring-boot:run`, local
 * profile, which allows the e2e origin http://localhost:5174) and use the installed Chrome, so no browser is
 * downloaded. The STOMP connection goes through a proxy that adds network latency (e2e/latencyProxy.js); REST calls
 * go straight to the API.
 */
export default defineConfig({
  testDir: 'e2e',
  timeout: 180_000,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${E2E_APP_PORT}`,
    channel: 'chrome',
    headless: true,
  },
  webServer: {
    command: `npx vite --port ${E2E_APP_PORT} --strictPort`,
    url: `http://localhost:${E2E_APP_PORT}`,
    reuseExistingServer: false,
    timeout: 60_000,
    env: {
      VITE_API_URL: API_URL,
      // The proxy listens on IPv4 only, so the address does not depend on how localhost resolves
      VITE_WS_URL: `http://127.0.0.1:${PROXY_PORT}/ws`,
    },
  },
});

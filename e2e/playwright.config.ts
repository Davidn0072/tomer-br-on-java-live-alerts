import { defineConfig, devices } from '@playwright/test';

// Assumes the full stack is already running (`docker compose up -d --build --wait` from the
// repo root) — this suite drives the real containers, it doesn't start them itself, since
// Spring Boot/MSSQL cold-start time doesn't belong inside Playwright's own retry budget.
//
// workers: 1 is load-bearing, not a default: every spec shares the one live stack, and
// restart-recovery.spec.ts restarts the `server` container mid-test, which would break any
// other spec's WebSocket connection if it ran concurrently in another worker.
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});

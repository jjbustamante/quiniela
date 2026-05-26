import { defineConfig, devices } from '@playwright/test';

// See brain/plugins/tech/nextjs-cicd/ci/e2e-tests.md.
// Tests run against the PRODUCTION server (`next start`), NEVER `next dev`.
//
// Set PLAYWRIGHT_BASE_URL to point at an already-running server on a
// custom port (e.g. http://localhost:3003) when 3000 is taken locally.
// When set, Playwright reuses that server instead of starting its own.

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000';

export default defineConfig({
  testDir: './e2e',
  // Playwright's default testMatch is *.spec.{ts,tsx} / *.test.{ts,tsx}.
  // We use *.e2e.{ts,tsx} to keep Vitest unit tests and Playwright E2E tests
  // visually distinct — set testMatch so Playwright actually picks them up.
  testMatch: /.*\.e2e\.(ts|tsx)$/,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI ? [['github'], ['html']] : 'html',

  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    // Uncomment when cross-browser parity matters:
    // { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    // { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],

  // CI builds + starts; local reuses an already-running `npm run dev` or `npm start`.
  // The landing page fetches the backend's /actuator/health as a Server Component,
  // so failure modes there will surface — but the page should still render.
  webServer: {
    command: process.env.CI ? 'npm run build && npm start' : 'npm start',
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});

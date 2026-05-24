import type { RequestHandler } from 'msw';

// MSW v2 handlers — populate per feature.
//
// Example:
//   import { http, HttpResponse } from 'msw';
//
//   export const handlers: RequestHandler[] = [
//     http.get('http://localhost:8080/actuator/health', () =>
//       HttpResponse.json({ status: 'UP' })
//     ),
//   ];
//
// onUnhandledRequest: 'error' (set in vitest.setup.ts) will fail tests that hit
// unmocked URLs — that's intentional. Silent passthrough hides bugs.

export const handlers: RequestHandler[] = [];

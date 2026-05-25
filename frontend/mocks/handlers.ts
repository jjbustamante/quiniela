import { http, HttpResponse } from 'msw';
import type { RequestHandler } from 'msw';

// MSW v2 handlers — populate per feature.
//
// onUnhandledRequest: 'error' (set in vitest.setup.ts) will fail tests that hit
// unmocked URLs — that's intentional. Silent passthrough hides bugs.

const apiBase = process.env.API_URL ?? "http://localhost:8080";

export const handlers: RequestHandler[] = [
  http.get(`${apiBase}/api/invite/:path`, ({ params }) => {
    if (params.path === "valid-captain-abc") {
      return HttpResponse.json({
        valid: true,
        inviterDisplayName: "Andrés",
        inviterRole: "CAPTAIN",
      });
    }
    return new HttpResponse(null, { status: 404 });
  }),

  http.get(`${apiBase}/api/me`, () =>
    HttpResponse.json({
      id: 1,
      email: "juan@example.com",
      displayName: "Juan",
      avatarUrl: null,
      role: "ADMIN",
      invitePath: "juan-abc123",
      canInvite: true,
      invitedByUserId: null,
    }),
  ),
];

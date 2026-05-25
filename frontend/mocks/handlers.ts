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

  http.get(`${apiBase}/api/bracket/me`, () =>
    HttpResponse.json({
      quinielaId: 1,
      totalMatches: 104,
      totalBets: 0,
      groups: ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"].map((code) => ({
        code,
        filled: 0,
        total: 6,
        matches: [],
      })),
      knockouts: [
        { code: "R32", name: "Dieciseisavos", filled: 0, total: 16, unlocked: false, matches: [] },
        { code: "R16", name: "Octavos", filled: 0, total: 8, unlocked: false, matches: [] },
        { code: "QF", name: "Cuartos", filled: 0, total: 4, unlocked: false, matches: [] },
        { code: "SF", name: "Semifinales", filled: 0, total: 2, unlocked: false, matches: [] },
        { code: "THIRD_PLACE", name: "Tercer puesto", filled: 0, total: 1, unlocked: false, matches: [] },
        { code: "FINAL", name: "Final", filled: 0, total: 1, unlocked: false, matches: [] },
      ],
    }),
  ),

  http.post(`${apiBase}/api/bracket/bet`, async ({ request }) => {
    const body = (await request.json()) as { matchId: number; scoreT1: number; scoreT2: number };
    return HttpResponse.json({ matchId: body.matchId, scoreT1: body.scoreT1, scoreT2: body.scoreT2 });
  }),

  http.post(`${apiBase}/api/paul/suggest`, ({ request }) => {
    const url = new URL(request.url);
    const matchId = Number(url.searchParams.get("matchId"));
    return HttpResponse.json({
      scoreT1: (matchId * 17) % 4,
      scoreT2: (matchId * 3) % 3,
      reasoning: "Mock Paul",
    });
  }),

  http.post(`${apiBase}/api/paul/fill`, () =>
    HttpResponse.json({ created: 72 }),
  ),
];

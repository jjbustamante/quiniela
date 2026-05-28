import { api } from "./client";

export type PrizeSplitEntry = {
  rank: number;
  percentage: number;
  payoutCents: number;
};

export type PublicSummary = {
  tournament: {
    slug: string;
    name: string;
    startDate: string;
    endDate: string;
    hostCountryCodes: string[];
    openingVenue: string | null;
    totalGroupStageMatches: number;
    totalGroups: number;
  };
  pool: {
    currency: string;
    entryFeeCents: number;
    potCents: number;
    panaCount: number;
  };
  prizeSplit: PrizeSplitEntry[];
};

/**
 * Static fallback used when the backend is unreachable (CI e2e job, prod
 * outage). Stable WC-2026 facts so the public landing still renders the
 * right tournament identity instead of 500ing. Revisit when v2 (UCL etc.)
 * lands — at that point the fallback either rotates per slug or we accept
 * a more conservative "—" rendering.
 */
const FALLBACK: PublicSummary = {
  tournament: {
    slug: "fifa-wc-2026",
    name: "Copa Mundial FIFA 2026",
    startDate: "2026-06-11",
    endDate: "2026-07-19",
    hostCountryCodes: ["USA", "CAN", "MEX"],
    openingVenue: null,
    totalGroupStageMatches: 72,
    totalGroups: 12,
  },
  pool: { currency: "USD", entryFeeCents: 2000, potCents: 0, panaCount: 0 },
  // Mirrors the V003 seed (80/15/5). When the backend is down we still want
  // /ranking to render the same shape with $0 payouts, so the page doesn't
  // suddenly grow/shrink rows on a transient outage.
  prizeSplit: [
    { rank: 1, percentage: 80, payoutCents: 0 },
    { rank: 2, percentage: 15, payoutCents: 0 },
    { rank: 3, percentage: 5, payoutCents: 0 },
  ],
};

export async function getPublicSummary(): Promise<PublicSummary> {
  return api<PublicSummary>("/api/public/summary", { authed: false });
}

/**
 * Same as `getPublicSummary` but returns the static fallback on any error.
 * Use on public pages where rendering must never be blocked by a backend
 * outage (landing page).
 */
export async function getPublicSummaryOrFallback(): Promise<PublicSummary> {
  try {
    return await getPublicSummary();
  } catch (err) {
    // One-line log on purpose — the stack adds noise to the e2e log without
    // saying anything useful (fetch failed → ECONNREFUSED is self-evident).
    const reason = err instanceof Error ? err.message : String(err);
    console.warn(`[summary] backend unreachable (${reason}); using fallback`);
    return FALLBACK;
  }
}

"use server";

import { getMatchPicks } from "@/lib/api/compare";
import type { MatchPicksView } from "@/lib/api/compare";
import { ApiError } from "@/lib/api/client";

export async function fetchMatchPicks(matchId: number): Promise<MatchPicksView | null> {
  try {
    return await getMatchPicks(matchId);
  } catch (e) {
    if (e instanceof ApiError && e.status === 403) return null;
    throw e;
  }
}

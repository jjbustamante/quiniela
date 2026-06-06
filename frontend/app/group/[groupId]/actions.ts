"use server";

import { revalidatePath } from "next/cache";
import { saveBet } from "@/lib/api/bracket";
import { ignoreLockedRace } from "@/lib/api/ignore-locked";
import { suggestForMatch } from "@/lib/api/paul";
import type { AcceptOutcome } from "@/lib/paul-feedback";
import { ApiError } from "@/lib/api/client";

export async function saveBetAction(
  matchId: number,
  scoreT1: number,
  scoreT2: number,
  groupId: string,
) {
  try {
    await saveBet(matchId, scoreT1, scoreT2);
    revalidatePath(`/group/${groupId}`);
  } catch (e) {
    ignoreLockedRace(e);
  }
}

export async function acceptPaulSuggestionAction(
  matchId: number,
  groupId: string,
): Promise<AcceptOutcome> {
  try {
    const s = await suggestForMatch(matchId);
    await saveBet(matchId, s.scoreT1, s.scoreT2);
    revalidatePath(`/group/${groupId}`);
    return { ok: true, scoreT1: s.scoreT1, scoreT2: s.scoreT2, reasoning: s.reasoning };
  } catch (e) {
    if (e instanceof ApiError) return { ok: false, locked: e.status === 423 };
    return { ok: false, locked: false };
  }
}

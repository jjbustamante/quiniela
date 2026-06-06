"use server";

import { revalidatePath } from "next/cache";
import { saveBet } from "@/lib/api/bracket";
import { ignoreLockedRace } from "@/lib/api/ignore-locked";
import { suggestForMatch } from "@/lib/api/paul";

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

export async function acceptPaulSuggestionAction(matchId: number, groupId: string) {
  try {
    const s = await suggestForMatch(matchId);
    await saveBet(matchId, s.scoreT1, s.scoreT2);
    revalidatePath(`/group/${groupId}`);
  } catch (e) {
    ignoreLockedRace(e);
  }
}

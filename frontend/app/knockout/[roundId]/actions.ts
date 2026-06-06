"use server";

import { revalidatePath } from "next/cache";
import { saveBet } from "@/lib/api/bracket";
import { ignoreLockedRace } from "@/lib/api/ignore-locked";
import { suggestForMatch } from "@/lib/api/paul";

export async function saveBetAction(
  matchId: number,
  scoreT1: number,
  scoreT2: number,
  roundCode: string,
  predictedWinnerId?: number | null,
) {
  try {
    await saveBet(matchId, scoreT1, scoreT2, predictedWinnerId);
    revalidatePath(`/knockout/${roundCode}`);
  } catch (e) {
    ignoreLockedRace(e);
  }
}

export async function acceptPaulSuggestionAction(matchId: number, roundCode: string) {
  try {
    const s = await suggestForMatch(matchId);
    await saveBet(matchId, s.scoreT1, s.scoreT2);
    revalidatePath(`/knockout/${roundCode}`);
  } catch (e) {
    ignoreLockedRace(e);
  }
}

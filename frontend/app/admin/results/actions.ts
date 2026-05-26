"use server";

import { revalidatePath } from "next/cache";
import { recordMatchResult } from "@/lib/api/admin";

export async function saveResultAction(
  matchId: number,
  scoreT1: number,
  scoreT2: number,
) {
  if (!Number.isInteger(scoreT1) || !Number.isInteger(scoreT2)) {
    throw new Error("Scores must be integers");
  }
  if (scoreT1 < 0 || scoreT2 < 0) {
    throw new Error("Scores must be non-negative");
  }
  const result = await recordMatchResult(matchId, scoreT1, scoreT2);
  // Re-render the page so the saved row reflects the trigger's effect.
  revalidatePath("/admin/results");
  return result;
}

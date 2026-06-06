"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "@/lib/api/client";
import { fillAll } from "@/lib/api/paul";

export type PaulFillResult =
  | { ok: true; created: number }
  | { ok: false; locked: boolean; error: string };

/**
 * "Paul fills it all". The backend returns 423 when the round is locked (the
 * bracket save path enforces the betting deadline); catch the ApiError and
 * return a result instead of letting it bubble — an uncaught server-action
 * error renders as HTTP 500. The button surfaces the locked case to the user.
 */
export async function paulFillAllAction(): Promise<PaulFillResult> {
  try {
    const result = await fillAll();
    // The button lives on /groups now (and the home dashboard reflects fill counts);
    // revalidate both so the group cards' filled/total update after Paul fills.
    revalidatePath("/groups");
    revalidatePath("/home");
    return { ok: true, created: result.created };
  } catch (e) {
    if (e instanceof ApiError) {
      return { ok: false, locked: e.status === 423, error: e.message };
    }
    throw e;
  }
}

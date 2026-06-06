"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "@/lib/api/client";
import { fillAll } from "@/lib/api/paul";

/**
 * "Paul fills it all". The backend returns 423 when the round is locked (the
 * bracket save path enforces the betting deadline). Swallow that ApiError so
 * the server action returns instead of bubbling — an uncaught server-action
 * error becomes an HTTP 500. User-facing messaging for the locked case lives
 * with the Paul-feedback work; here we just keep the action from crashing.
 */
export async function paulFillAllAction(): Promise<void> {
  try {
    await fillAll();
    revalidatePath("/home");
  } catch (e) {
    if (e instanceof ApiError) return;
    throw e;
  }
}

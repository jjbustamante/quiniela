"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "@/lib/api/client";
import { updatePoolConfig, type PrizeRow } from "@/lib/api/pool-config";

export type SaveResult = { ok: true } | { ok: false; error: string };

export async function savePoolConfigAction(input: {
  entryFeeCents: number;
  houseCutPercentage: number;
  prizeSplit: PrizeRow[];
}): Promise<SaveResult> {
  try {
    await updatePoolConfig(input);
    revalidatePath("/admin/config");
    return { ok: true };
  } catch (e) {
    if (e instanceof ApiError) return { ok: false, error: e.message };
    throw e;
  }
}

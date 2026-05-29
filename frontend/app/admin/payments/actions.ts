"use server";

import { revalidatePath } from "next/cache";
import { markSettled, markPaid } from "@/lib/api/payments";

export async function markSettledAction(captainId: number, settled: boolean): Promise<void> {
  await markSettled(captainId, settled);
  revalidatePath("/admin/payments");
}

export async function markPaidAction(userId: number, paid: boolean): Promise<void> {
  await markPaid(userId, paid);
  revalidatePath("/admin/payments");
}

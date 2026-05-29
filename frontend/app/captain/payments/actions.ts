"use server";

import { revalidatePath } from "next/cache";
import { markPaid } from "@/lib/api/payments";

export async function markPaidAction(userId: number, paid: boolean): Promise<void> {
  await markPaid(userId, paid);
  revalidatePath("/captain/payments");
}

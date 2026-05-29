"use server";

import { revalidatePath } from "next/cache";
import { setTimezone } from "@/lib/api/settings";

export async function saveTimezoneAction(timezone: string): Promise<void> {
  await setTimezone(timezone);
  revalidatePath("/settings");
}

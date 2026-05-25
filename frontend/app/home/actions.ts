"use server";

import { revalidatePath } from "next/cache";
import { fillAll } from "@/lib/api/paul";

export async function paulFillAllAction() {
  await fillAll();
  revalidatePath("/home");
}

"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "@/lib/api/client";
import { setWhatsappVisibility } from "@/lib/api/captain-whatsapp";

export type ToggleResult = { ok: true } | { ok: false; error: string };

export async function setVisibilityAction(input: {
  userId: number;
  visible: boolean;
}): Promise<ToggleResult> {
  try {
    await setWhatsappVisibility(input);
    revalidatePath("/captain/whatsapp");
    return { ok: true };
  } catch (e) {
    if (e instanceof ApiError) return { ok: false, error: e.message };
    throw e;
  }
}

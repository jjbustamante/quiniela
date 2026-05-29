"use server";

import { revalidatePath } from "next/cache";
import {
  cleanTestData,
  setDeadlines,
  setTestMode,
  simulateAll,
  simulateRound,
} from "@/lib/api/test-mode";

export async function setModeAction(enabled: boolean): Promise<void> {
  await setTestMode(enabled);
  revalidatePath("/admin/test");
}

export async function cleanAction(): Promise<void> {
  await cleanTestData();
  revalidatePath("/admin/test");
}

export async function deadlinesAction(
  groupStageDeadline: string | null,
  knockoutDeadline: string | null,
): Promise<void> {
  await setDeadlines(groupStageDeadline, knockoutDeadline);
  revalidatePath("/admin/test");
}

export async function simulateRoundAction(): Promise<void> {
  await simulateRound();
  revalidatePath("/admin/test");
}

export async function simulateAllAction(): Promise<void> {
  await simulateAll();
  revalidatePath("/admin/test");
}

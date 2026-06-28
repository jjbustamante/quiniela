"use server";

import { ApiError } from "@/lib/api/client";
import {
  generatePaul,
  getPaulJobStatus,
  revealPaul,
  synthesizePaul,
  type PaulJobStatus,
  type PaulRevealResult,
} from "@/lib/api/paul-admin";

export type JobActionResult =
  | { ok: true; status: PaulJobStatus }
  | { ok: false; conflict: boolean };

export type RevealActionResult =
  | { ok: true; result: PaulRevealResult }
  | { ok: false; conflict: boolean };

async function startJob(start: () => Promise<PaulJobStatus>): Promise<JobActionResult> {
  try {
    return { ok: true, status: await start() };
  } catch (e) {
    // 409 = a Paul job is already running (backend allows one at a time).
    return { ok: false, conflict: e instanceof ApiError && e.status === 409 };
  }
}

export async function generateAction(): Promise<JobActionResult> {
  return startJob(generatePaul);
}

export async function synthesizeAction(): Promise<JobActionResult> {
  return startJob(synthesizePaul);
}

export async function revealAction(): Promise<RevealActionResult> {
  try {
    return { ok: true, result: await revealPaul() };
  } catch (e) {
    return { ok: false, conflict: e instanceof ApiError && e.status === 409 };
  }
}

/** Poll the live job status (used by the panel while a job is RUNNING). */
export async function statusAction(): Promise<PaulJobStatus> {
  return getPaulJobStatus();
}

import { api } from "./client";

export type PaulJobState = "IDLE" | "RUNNING" | "DONE" | "FAILED";

/** Snapshot of Paul's current/last batch job (mirrors backend PaulJobStatus). */
export type PaulJobStatus = {
  phase: string | null;
  state: PaulJobState;
  processed: number;
  total: number;
  error: string | null;
  startedAt: string | null;
  finishedAt: string | null;
};

export type PaulRevealResult = { betsCreated: number };

export function getPaulJobStatus(): Promise<PaulJobStatus> {
  return api<PaulJobStatus>("/api/admin/paul/status");
}

/** Start candidate generation. Backend returns 202 with the initial RUNNING status. */
export function generatePaul(): Promise<PaulJobStatus> {
  return api<PaulJobStatus>("/api/admin/paul/generate", { method: "POST" });
}

/** Start official-pick synthesis. Backend returns 202 with the initial RUNNING status. */
export function synthesizePaul(): Promise<PaulJobStatus> {
  return api<PaulJobStatus>("/api/admin/paul/synthesize", { method: "POST" });
}

/** Snapshot Paul's official picks into his own bets (synchronous). */
export function revealPaul(): Promise<PaulRevealResult> {
  return api<PaulRevealResult>("/api/admin/paul/reveal", { method: "POST" });
}

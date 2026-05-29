import { api } from "./client";

export type TestState = {
  testMode: boolean;
  groupStageDeadline: string | null;
  knockoutDeadline: string | null;
  currentRoundCode: string | null;
  roundsRemaining: number;
};

export async function getTestState(): Promise<TestState> {
  return api<TestState>("/api/admin/test/state");
}

export async function setTestMode(enabled: boolean): Promise<void> {
  await api("/api/admin/test/mode", { method: "PUT", body: JSON.stringify({ enabled }) });
}

export async function cleanTestData(): Promise<void> {
  await api("/api/admin/test/clean", { method: "POST" });
}

export async function setDeadlines(
  groupStageDeadline: string | null,
  knockoutDeadline: string | null,
): Promise<void> {
  await api("/api/admin/test/deadlines", {
    method: "PUT",
    body: JSON.stringify({ groupStageDeadline, knockoutDeadline }),
  });
}

export async function simulateRound(): Promise<void> {
  await api("/api/admin/test/simulate/round", { method: "POST" });
}

export async function simulateAll(): Promise<void> {
  await api("/api/admin/test/simulate/all", { method: "POST" });
}

import { api } from "./client";

export type PaulSuggestion = {
  scoreT1: number;
  scoreT2: number;
  reasoning: string;
  predictedWinnerId: number | null;
};

export type PaulFillResult = { created: number };

export async function suggestForMatch(matchId: number): Promise<PaulSuggestion> {
  return api<PaulSuggestion>(`/api/paul/suggest?matchId=${matchId}`, { method: "POST" });
}

export async function fillAll(): Promise<PaulFillResult> {
  return api<PaulFillResult>("/api/paul/fill", { method: "POST" });
}

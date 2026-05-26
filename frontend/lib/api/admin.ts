import { api } from "./client";

export type AdminTeamSummary = {
  id: number;
  code: string | null;
  name: string | null;
  flag: string | null;
} | null;

export type AdminMatchRow = {
  matchId: number;
  roundCode: string;
  roundName: string;
  roundSequence: number;
  groupCode: string | null;
  kickoffAt: string;
  team1: AdminTeamSummary;
  team2: AdminTeamSummary;
  scoreT1: number | null;
  scoreT2: number | null;
  winnerId: number | null;
  played: boolean;
};

export type AdminMatchResult = {
  matchId: number;
  scoreT1: number;
  scoreT2: number;
  winnerId: number | null;
  played: boolean;
};

export async function getAdminMatches(): Promise<AdminMatchRow[]> {
  return api<AdminMatchRow[]>("/api/admin/matches");
}

export async function recordMatchResult(
  matchId: number,
  scoreT1: number,
  scoreT2: number,
): Promise<AdminMatchResult> {
  return api<AdminMatchResult>(`/api/admin/matches/${matchId}/result`, {
    method: "PUT",
    body: JSON.stringify({ scoreT1, scoreT2 }),
  });
}

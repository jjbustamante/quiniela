import { api } from "./client";

export type ScoreBreakdown = {
  outcome: number;
  team1Exact: number;
  team2Exact: number;
  goalDiff: number;
  multiplier: number;
  total: number;
};

export type TeamRef = { code: string | null; name: string | null; flag: string | null };

export type MatchScore = {
  matchId: number;
  team1: TeamRef;
  team2: TeamRef;
  kickoffAt: string;
  betScoreT1: number | null;
  betScoreT2: number | null;
  actualScoreT1: number | null;
  actualScoreT2: number | null;
  breakdown: ScoreBreakdown;
  note?: 'PLACED_AFTER_KICKOFF' | 'EDITED_AFTER_KICKOFF' | null;
};

export type StageScore = {
  roundCode: string;
  roundName: string;
  points: number;
  matches: MatchScore[];
};

export type Scorecard = {
  userId: number;
  displayName: string | null;
  totalPoints: number;
  stages: StageScore[];
};

export async function getScorecard(userId: number): Promise<Scorecard> {
  return api<Scorecard>(`/api/ranking/${userId}/scorecard`);
}

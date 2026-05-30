import { api } from "./client";

export type ScoreCount = { scoreT1: number; scoreT2: number; count: number };

export type MatchConsensus = {
  matchId: number;
  roundCode: string;
  team1Code: string | null;
  team1Flag: string | null;
  team2Code: string | null;
  team2Flag: string | null;
  kickoffAt: string;
  actualScoreT1: number | null;
  actualScoreT2: number | null;
  played: boolean;
  revealed: boolean;
  myScoreT1: number | null;
  myScoreT2: number | null;
  distribution: ScoreCount[];
  totalPicks: number;
  majority: boolean;
  rebel: boolean;
};

export type GroupConsensusView = { matches: MatchConsensus[] };

export type H2HMatchState = "agree" | "differ" | "hidden";

export type H2HMatch = {
  matchId: number;
  roundCode: string;
  team1Code: string | null;
  team1Flag: string | null;
  team2Code: string | null;
  team2Flag: string | null;
  kickoffAt: string;
  actualScoreT1: number | null;
  actualScoreT2: number | null;
  played: boolean;
  revealed: boolean;
  myScoreT1: number | null;
  myScoreT2: number | null;
  rivalScoreT1: number | null;
  rivalScoreT2: number | null;
  state: H2HMatchState;
};

export type H2HView = {
  rivalUserId: number;
  rivalDisplayName: string | null;
  agreeCount: number;
  differCount: number;
  myPoints: number | null;
  rivalPoints: number | null;
  matches: H2HMatch[];
};

export async function getGroupConsensus(): Promise<GroupConsensusView> {
  return api<GroupConsensusView>("/api/compare/group");
}

export async function getH2H(vs: number): Promise<H2HView> {
  return api<H2HView>(`/api/compare/h2h?vs=${vs}`);
}

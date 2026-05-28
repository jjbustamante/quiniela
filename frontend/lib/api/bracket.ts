import { api } from "./client";

export type MatchView = {
  id: number;
  team1Code: string | null;
  team1Name: string | null;
  team1Flag: string | null;
  team2Code: string | null;
  team2Name: string | null;
  team2Flag: string | null;
  kickoffAt: string;
  betScoreT1: number | null;
  betScoreT2: number | null;
  actualScoreT1: number | null;
  actualScoreT2: number | null;
  played: boolean;
};

export type GroupView = {
  code: string;
  filled: number;
  total: number;
  locked: boolean;
  matches: MatchView[];
};

export type KnockoutRoundView = {
  code: string;
  name: string;
  filled: number;
  total: number;
  unlocked: boolean;
  locked: boolean;
  matches: MatchView[];
};

export type BracketView = {
  quinielaId: number;
  totalMatches: number;
  totalBets: number;
  groupStageDeadline: string | null;
  knockoutDeadline: string | null;
  groups: GroupView[];
  knockouts: KnockoutRoundView[];
};

export type BetView = {
  matchId: number;
  scoreT1: number;
  scoreT2: number;
};

export async function getMyBracket(): Promise<BracketView> {
  return api<BracketView>("/api/bracket/me");
}

export async function saveBet(
  matchId: number,
  scoreT1: number,
  scoreT2: number,
): Promise<BetView> {
  return api<BetView>("/api/bracket/bet", {
    method: "POST",
    body: JSON.stringify({ matchId, scoreT1, scoreT2 }),
  });
}

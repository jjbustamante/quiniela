import { api } from "./client";

export type TeamRef = {
  code: string | null;
  name: string | null;
  flag: string | null;
};

export type ScorePair = {
  t1: number | null;
  t2: number | null;
};

export type MatchView = {
  id: number;
  roundCode: string;
  groupCode: string | null;
  kickoffAt: string;
  team1: TeamRef;
  team2: TeamRef;
  /** Actual final score (null when not played). */
  score: ScorePair | null;
  played: boolean;
  /** Caller's prediction (null when no bet placed). */
  yourPick: ScorePair | null;
  /** Points awarded by V010's score_match_for_bet. Null when match unplayed or no bet. */
  pointsEarned: number | null;
};

export type MatchesView = {
  /** Server wall-clock instant; used by the UI for live-match comparison so client + server agree. */
  serverTime: string;
  past: MatchView[];
  today: MatchView[];
  upcoming: MatchView[];
};

export async function getMatches(): Promise<MatchesView> {
  return api<MatchesView>("/api/matches");
}

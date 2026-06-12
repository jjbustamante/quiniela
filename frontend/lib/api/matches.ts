import { api } from "./client";
import type { ScoreBreakdown } from "./scorecard";

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
  /** Per-match point components (caller's own pick). Null when unplayed or no bet. */
  breakdown: ScoreBreakdown | null;
  /**
   * True when the match is currently in-play: has a score but is not yet `played`.
   * When true, `score` holds the live score and `pointsEarned` holds provisional points.
   */
  live: boolean;
  /**
   * Team the caller predicted to advance when their knockout pick was a draw.
   * Null for group bets, non-draw picks, or no bet. (Actual knockout results are
   * never stored as draws — penalties are folded into the score — so there is no
   * symmetric "actual draw winner" field.)
   */
  pickWinner: TeamRef | null;
  /**
   * Team that actually advanced when a knockout result is a draw (penalties).
   * Null for group matches and decisive results (the score already names the winner).
   */
  winner: TeamRef | null;
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

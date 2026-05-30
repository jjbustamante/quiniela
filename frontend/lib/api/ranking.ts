import { api } from "./client";

export type RankingEntry = {
  rank: number;
  userId: number;
  displayName: string | null;
  points: number;
  /** Rank change vs previous round. Null in v1 until the snapshot table lands. */
  delta: number | null;
  isYou: boolean;
  /** True for Pulpo Paul — shown on the board but never prize-eligible. */
  isBot: boolean;
};

export type RankingView = {
  entries: RankingEntry[];
  updatedAt: string;
};

export async function getRanking(): Promise<RankingView> {
  return api<RankingView>("/api/ranking");
}

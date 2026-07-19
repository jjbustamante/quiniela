import type { RankingEntry } from "@/lib/api/ranking";
import type { PrizeSplitEntry } from "@/lib/api/summary";
import { formatPot } from "@/lib/tournament-format";

const MEDALS: Record<number, string> = { 1: "🥇", 2: "🥈", 3: "🥉" };

export type PrizeRankGroup = { rank: number; entries: RankingEntry[] };

/**
 * Groups prize-eligible entries (bots excluded) into competition-rank
 * positions 1..prizeRankCount. `entries` must already be sorted points DESC
 * (the ranking API guarantees this) — ties share a rank, same semantics as
 * SQL RANK(), recomputed over the bot-filtered subset so a bot occupying a
 * leaderboard slot never shifts a human's prize rank.
 */
export function computePrizeRanks(entries: RankingEntry[], prizeRankCount: number): PrizeRankGroup[] {
  const groups: PrizeRankGroup[] = [];
  let rank = 0;
  let lastPoints: number | null = null;
  let seen = 0;
  for (const e of entries) {
    if (e.isBot) continue;
    seen += 1;
    if (e.points !== lastPoints) {
      rank = seen;
      lastPoints = e.points;
    }
    if (rank > prizeRankCount) break;
    let group = groups.find((g) => g.rank === rank);
    if (!group) {
      group = { rank, entries: [] };
      groups.push(group);
    }
    group.entries.push(e);
  }
  return groups;
}

/**
 * Build "🥇 $24"-style labels keyed by userId for the prize positions (1–3),
 * applying two rules on top of the raw prize split:
 *
 *  - **No standings yet:** until at least one prize-eligible player has scored,
 *    return an empty map. Before the first result every player sits at rank 1
 *    with 0 points, so showing "🥇 $208" to all of them is meaningless.
 *  - **Tie at a prize rank:** show the medal only, no amount. The prize for a
 *    shared position is split between the tied players, so printing the full
 *    amount next to each would read as "everyone wins the whole thing".
 *
 * The bot (Pulpo Paul) is never prize-eligible, so it counts neither toward
 * "standings are real" nor toward a tie — and, via computePrizeRanks, never
 * occupies a prize position itself even when it tops the raw leaderboard.
 */
export function buildPayoutLabels(
  entries: RankingEntry[],
  prizeSplit: PrizeSplitEntry[],
  currency: string,
): Map<number, string> {
  const labels = new Map<number, string>();

  const standingsAreReal = entries.some((e) => !e.isBot && e.points > 0);
  if (!standingsAreReal) return labels;

  const prizeRanks = computePrizeRanks(entries, prizeSplit.length);
  for (const group of prizeRanks) {
    const split = prizeSplit[group.rank - 1];
    const medal = MEDALS[group.rank];
    if (!split || !medal) continue;
    const tied = group.entries.length > 1;
    const label = tied ? medal : `${medal} ${formatPot(split.payoutCents, currency)}`;
    for (const e of group.entries) labels.set(e.userId, label);
  }

  return labels;
}

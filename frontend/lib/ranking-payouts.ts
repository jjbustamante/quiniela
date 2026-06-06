import type { RankingEntry } from "@/lib/api/ranking";
import type { PrizeSplitEntry } from "@/lib/api/summary";
import { formatPot } from "@/lib/tournament-format";

const MEDALS: Record<number, string> = { 1: "🥇", 2: "🥈", 3: "🥉" };

/**
 * Build "🥇 $24"-style labels keyed by rank for the prize positions (1–3),
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
 * "standings are real" nor toward a tie.
 */
export function buildPayoutLabels(
  entries: RankingEntry[],
  prizeSplit: PrizeSplitEntry[],
  currency: string,
): Map<number, string> {
  const labels = new Map<number, string>();

  const standingsAreReal = entries.some((e) => !e.isBot && e.points > 0);
  if (!standingsAreReal) return labels;

  const countByRank = new Map<number, number>();
  for (const e of entries) {
    if (e.isBot) continue;
    countByRank.set(e.rank, (countByRank.get(e.rank) ?? 0) + 1);
  }

  for (const split of prizeSplit) {
    const medal = MEDALS[split.rank];
    if (!medal) continue;
    const tied = (countByRank.get(split.rank) ?? 0) > 1;
    labels.set(split.rank, tied ? medal : `${medal} ${formatPot(split.payoutCents, currency)}`);
  }

  return labels;
}

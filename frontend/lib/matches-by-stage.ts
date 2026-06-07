import type { MatchView } from "@/lib/api/matches";

export type StageGroup = { roundCode: string; matches: MatchView[] };

/**
 * Group matches by stage (roundCode) for the "Por fase" view. Sections are
 * ordered most-recent-activity first: a stage whose latest STARTED match
 * (kickoff <= now) is most recent floats to the top (so after the group phase,
 * 16vos sits above Grupos). Stages with no started match sort after the started
 * ones, by their soonest kickoff. Matches within a stage are chronological.
 */
export function groupMatchesByStage(matches: MatchView[], nowMs: number): StageGroup[] {
  const byCode = new Map<string, MatchView[]>();
  for (const m of matches) {
    const arr = byCode.get(m.roundCode);
    if (arr) arr.push(m);
    else byCode.set(m.roundCode, [m]);
  }

  type Tagged = {
    roundCode: string;
    matches: MatchView[];
    latestStarted: number | null;
    earliest: number;
  };

  const groups: Tagged[] = [];
  for (const [roundCode, ms] of byCode) {
    const sorted = [...ms].sort((a, b) => Date.parse(a.kickoffAt) - Date.parse(b.kickoffAt));
    let latestStarted: number | null = null;
    let earliest = Infinity;
    for (const m of sorted) {
      const k = Date.parse(m.kickoffAt);
      earliest = Math.min(earliest, k);
      if (k <= nowMs) latestStarted = latestStarted == null ? k : Math.max(latestStarted, k);
    }
    groups.push({ roundCode, matches: sorted, latestStarted, earliest });
  }

  groups.sort((a, b) => {
    const aStarted = a.latestStarted != null;
    const bStarted = b.latestStarted != null;
    if (aStarted && bStarted) return b.latestStarted! - a.latestStarted!;
    if (aStarted) return -1;
    if (bStarted) return 1;
    return a.earliest - b.earliest;
  });

  return groups.map(({ roundCode, matches }) => ({ roundCode, matches }));
}

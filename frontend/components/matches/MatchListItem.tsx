import type { MatchView } from "@/lib/api/matches";

type Labels = {
  yourPick: string;
  result: string;
  noPick: string;
  live: string;
  /** "+{n} PTS" formatted with the actual number. */
  formatPoints: (n: number) => string;
  kickoff: string;
  groupLabel: string | null;
  /** Localized round name (e.g. "16vos", "Octavos") — never the raw code like "R32". */
  roundLabel: string;
};

/**
 * MatchListItem — one Estadio '26 poster row per match. Same border + paper
 * background as the bracket MatchRow but the layout swaps the score button
 * for a static result block. Live indicator: red dot when kickoff has
 * passed but `played` is still false (best-effort, computed at render).
 */
export function MatchListItem({
  match,
  labels,
  showResult,
  now,
}: {
  match: MatchView;
  labels: Labels;
  /** Past + today tabs show the actual result column. Upcoming hides it. */
  showResult: boolean;
  /** Server-rendered "now" so the live dot is deterministic per request. */
  now: number;
}) {
  const isLive =
    !match.played && new Date(match.kickoffAt).getTime() <= now && showResult;

  // A draw pick on a knockout names who you think advances on penalties. The
  // score pair alone hides that, so surface the predicted team beside the pick —
  // mirrors group/MatchRow's advancing-team affordance on the betting side.
  const pickIsDraw = match.yourPick != null && match.yourPick.t1 === match.yourPick.t2;
  const pickAdvancingTeam = pickIsDraw ? match.pickWinner : null;

  // Knockout draws are decided on penalties — the score hides who advanced, so
  // surface it. Only for draws; a decisive score already names its winner.
  const resultIsDraw =
    match.played && match.score != null && match.score.t1 === match.score.t2;
  const advancingTeam = resultIsDraw ? match.winner : null;

  return (
    <div className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)]">
      <div className="flex items-stretch">
        {/* Team 1 */}
        <div className="flex min-w-0 flex-1 flex-col justify-between border-r-[1.5px] border-[var(--color-line-ink)] p-2.5">
          <span className="chrome-label chrome-label-muted block truncate">{labels.kickoff}</span>
          <div className="mt-1.5 flex min-w-0 items-center gap-1.5">
            <span className="shrink-0 text-[22px] leading-none">{match.team1.flag}</span>
            <span className="min-w-0 truncate font-display text-[15px] font-extrabold uppercase tracking-tight">
              {match.team1.name ?? "—"}
            </span>
          </div>
        </div>

        {/* Score / kickoff stub */}
        <div className="flex w-[110px] shrink-0 flex-col items-center justify-center gap-0.5 bg-[var(--color-bg-paper)] px-2 py-2">
          {match.played && match.score ? (
            <>
              <span className="font-display text-[26px] font-black leading-none tracking-[-0.04em] text-[var(--color-text-primary)]">
                {match.score.t1}–{match.score.t2}
              </span>
              {advancingTeam && (
                <span className="flex items-center gap-0.5 font-mono text-[9px] font-bold tracking-[0.06em] text-[var(--color-text-muted)]">
                  <span className="text-[11px] leading-none">{advancingTeam.flag}</span>
                  <span className="max-w-[64px] truncate">{advancingTeam.name}</span>
                </span>
              )}
            </>
          ) : isLive ? (
            <span className="inline-flex items-center gap-1.5 font-mono text-[10px] font-bold uppercase tracking-[0.12em] text-[var(--color-accent-red)]">
              <span className="inline-block h-2 w-2 rounded-full bg-[var(--color-accent-red)]" />
              {labels.live}
            </span>
          ) : (
            <span className="chrome-label chrome-label-muted">VS</span>
          )}
          {labels.groupLabel && (
            <span className="chrome-label chrome-label-muted">{labels.groupLabel}</span>
          )}
        </div>

        {/* Team 2 */}
        <div className="flex min-w-0 flex-1 flex-col items-end justify-between border-l-[1.5px] border-[var(--color-line-ink)] p-2.5">
          <span className="chrome-label chrome-label-muted block w-full truncate text-right">
            {labels.roundLabel}
          </span>
          <div className="mt-1.5 flex min-w-0 items-center gap-1.5">
            <span className="min-w-0 truncate font-display text-[15px] font-extrabold uppercase tracking-tight">
              {match.team2.name ?? "—"}
            </span>
            <span className="shrink-0 text-[22px] leading-none">{match.team2.flag}</span>
          </div>
        </div>
      </div>

      {/* Pick row — always present so layout doesn't reflow as picks land */}
      <div className="flex items-center justify-between border-t-[1.5px] border-dashed border-[var(--color-line-ink)] px-3 py-2 font-mono text-[10px] font-bold uppercase tracking-[0.12em] text-[var(--color-text-muted)]">
        <span className="inline-flex items-center gap-1.5">
          {labels.yourPick}:{" "}
          <span className="text-[var(--color-text-primary)]">
            {match.yourPick
              ? `${match.yourPick.t1}–${match.yourPick.t2}`
              : labels.noPick}
          </span>
          {pickAdvancingTeam && (
            <span className="inline-flex items-center gap-0.5 text-[var(--color-text-primary)]">
              <span className="text-[12px] leading-none">{pickAdvancingTeam.flag}</span>
              <span className="max-w-[64px] truncate">{pickAdvancingTeam.name}</span>
            </span>
          )}
        </span>
        {match.pointsEarned != null && (
          <span className="bg-[var(--color-accent-gold)] px-1.5 py-0.5 text-[var(--color-text-primary)]">
            {labels.formatPoints(match.pointsEarned)}
          </span>
        )}
      </div>
    </div>
  );
}

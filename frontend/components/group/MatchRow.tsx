import type { ReactNode } from "react";
import type { MatchView } from "@/lib/api/bracket";
import { PaulBadge } from "@/components/PaulMascot";

/**
 * MatchRow — Estadio '26 poster row. Three cells in the main row:
 *   [team1 + kickoff metadata] [score button] [team2 + venue metadata]
 * Then a dashed bottom row hosting the Paul affordance — labeled, not a
 * floating corner badge, so it reads as a real parallel action.
 *
 * Background tints behind filled team names use a 14/255 alpha of the
 * team's brand hex (passed in via the MatchView extension). When that
 * field isn't present, the tint just doesn't render.
 */
export function MatchRow({
  match,
  onTapScore,
  onAskPaul,
  team1Hex,
  team2Hex,
  kickoffLabel,
  venueLabel,
  paulLabelEmpty,
  paulLabelFilled,
  locked = false,
  paulPending = false,
  feedback,
}: {
  match: MatchView;
  onTapScore: () => void;
  onAskPaul: () => void;
  team1Hex?: string;
  team2Hex?: string;
  kickoffLabel?: string;
  venueLabel?: string;
  paulLabelEmpty: string;
  paulLabelFilled: string;
  locked?: boolean;
  paulPending?: boolean;
  feedback?: ReactNode;
}) {
  const filled = match.betScoreT1 != null && match.betScoreT2 != null;
  const t1Tint = filled && team1Hex ? `${team1Hex}14` : "transparent";
  const t2Tint = filled && team2Hex ? `${team2Hex}14` : "transparent";

  const isDraw = filled && match.betScoreT1 === match.betScoreT2;
  const advancingTeam =
    isDraw && match.betPredictedWinnerId != null
      ? match.betPredictedWinnerId === match.team1Id
        ? { flag: match.team1Flag, name: match.team1Name }
        : { flag: match.team2Flag, name: match.team2Name }
      : null;

  return (
    <div className={`border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] ${locked ? "opacity-80" : ""}`}>
      <div className="flex items-stretch">
        {/* Team 1 — flag + name + kickoff label */}
        <div
          className="flex min-w-0 flex-1 flex-col justify-between border-r-[1.5px] border-[var(--color-line-ink)] p-2.5"
          style={{ background: t1Tint }}
        >
          <span className="chrome-label chrome-label-muted block truncate">{kickoffLabel}</span>
          <div className="mt-1.5 flex min-w-0 items-center gap-1.5">
            <span className="shrink-0 text-[22px] leading-none">{match.team1Flag}</span>
            <span className="min-w-0 truncate font-display text-[15px] font-extrabold uppercase tracking-tight">
              {match.team1Name}
            </span>
          </div>
        </div>

        {/* Score */}
        <button
          type="button"
          onClick={onTapScore}
          disabled={locked}
          aria-disabled={locked || undefined}
          className={`flex w-[86px] shrink-0 flex-col items-center justify-center gap-0.5 font-display text-[26px] font-black leading-none tracking-[-0.04em] ${
            filled
              ? "bg-[var(--color-bg-ink)] text-[var(--color-accent-gold)]"
              : "bg-[var(--color-bg-paper)] text-[var(--color-text-muted)]"
          } ${locked ? "cursor-not-allowed" : ""}`}
        >
          {filled ? `${match.betScoreT1}–${match.betScoreT2}` : "_·_"}
          {advancingTeam && (
            <span className="flex items-center gap-0.5 font-mono text-[9px] font-bold tracking-[0.06em] text-[var(--color-accent-gold)]/70">
              <span className="text-[11px] leading-none">{advancingTeam.flag}</span>
              <span className="truncate max-w-[56px]">{advancingTeam.name}</span>
            </span>
          )}
        </button>

        {/* Team 2 — name + flag + venue label */}
        <div
          className="flex min-w-0 flex-1 flex-col items-end justify-between border-l-[1.5px] border-[var(--color-line-ink)] p-2.5"
          style={{ background: t2Tint }}
        >
          <span className="chrome-label chrome-label-muted block w-full truncate text-right">{venueLabel}</span>
          <div className="mt-1.5 flex min-w-0 items-center gap-1.5">
            <span className="min-w-0 truncate font-display text-[15px] font-extrabold uppercase tracking-tight">
              {match.team2Name}
            </span>
            <span className="shrink-0 text-[22px] leading-none">{match.team2Flag}</span>
          </div>
        </div>
      </div>

      {/* Paul row — labeled action, sits parallel to tapping the score. */}
      <button
        type="button"
        onClick={onAskPaul}
        disabled={locked || paulPending}
        aria-disabled={locked || paulPending || undefined}
        className={`flex w-full items-center justify-between border-t-[1.5px] border-dashed border-[var(--color-line-ink)] px-3 py-2 font-mono text-[10px] font-bold uppercase tracking-[0.12em] text-[var(--color-text-muted)] ${
          locked || paulPending ? "cursor-not-allowed" : "hover:bg-[var(--color-bg-primary)]"
        }`}
      >
        <span className="inline-flex items-center gap-2">
          <PaulBadge size={18} />
          {filled ? paulLabelFilled : paulLabelEmpty}
        </span>
        {paulPending ? (
          <span
            aria-label="cargando"
            className="inline-block h-3 w-3 animate-spin rounded-full border-[1.5px] border-[var(--color-text-primary)] border-t-transparent"
          />
        ) : (
          <span className="text-[var(--color-text-primary)]">→</span>
        )}
      </button>
      {feedback}
    </div>
  );
}

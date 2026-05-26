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
}) {
  const filled = match.betScoreT1 != null && match.betScoreT2 != null;
  const t1Tint = filled && team1Hex ? `${team1Hex}14` : "transparent";
  const t2Tint = filled && team2Hex ? `${team2Hex}14` : "transparent";

  return (
    <div className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)]">
      <div className="flex items-stretch">
        {/* Team 1 — flag + name + kickoff label */}
        <div
          className="flex min-w-0 flex-1 flex-col justify-between border-r-[1.5px] border-[var(--color-line-ink)] p-2.5"
          style={{ background: t1Tint }}
        >
          <span className="chrome-label chrome-label-muted">{kickoffLabel}</span>
          <div className="mt-1.5 flex min-w-0 items-center gap-1.5">
            <span className="shrink-0 text-[22px] leading-none">{match.team1Flag}</span>
            <span className="truncate font-display text-[15px] font-extrabold uppercase tracking-tight">
              {match.team1Name}
            </span>
          </div>
        </div>

        {/* Score */}
        <button
          type="button"
          onClick={onTapScore}
          className={`w-[86px] shrink-0 font-display text-[26px] font-black leading-none tracking-[-0.04em] ${
            filled
              ? "bg-[var(--color-bg-ink)] text-[var(--color-accent-gold)]"
              : "bg-[var(--color-bg-paper)] text-[var(--color-text-muted)]"
          }`}
        >
          {filled ? `${match.betScoreT1}–${match.betScoreT2}` : "_·_"}
        </button>

        {/* Team 2 — name + flag + venue label */}
        <div
          className="flex min-w-0 flex-1 flex-col items-end justify-between border-l-[1.5px] border-[var(--color-line-ink)] p-2.5"
          style={{ background: t2Tint }}
        >
          <span className="chrome-label chrome-label-muted">{venueLabel}</span>
          <div className="mt-1.5 flex min-w-0 items-center gap-1.5">
            <span className="truncate font-display text-[15px] font-extrabold uppercase tracking-tight">
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
        className="flex w-full items-center justify-between border-t-[1.5px] border-dashed border-[var(--color-line-ink)] px-3 py-2 font-mono text-[10px] font-bold uppercase tracking-[0.12em] text-[var(--color-text-muted)] hover:bg-[var(--color-bg-primary)]"
      >
        <span className="inline-flex items-center gap-2">
          <PaulBadge size={18} />
          {filled ? paulLabelFilled : paulLabelEmpty}
        </span>
        <span className="text-[var(--color-text-primary)]">→</span>
      </button>
    </div>
  );
}

import type { MatchView } from "@/lib/api/bracket";

export function MatchRow({
  match,
  onTapScore,
  onAskPaul,
}: {
  match: MatchView;
  onTapScore: () => void;
  onAskPaul: () => void;
}) {
  const filled = match.betScoreT1 != null && match.betScoreT2 != null;
  return (
    <div className="flex items-center gap-3 rounded-md border border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] p-3">
      <div className="flex-1 text-right text-sm">
        <span className="mr-1">{match.team1Flag}</span>
        {match.team1Name}
      </div>
      <button
        type="button"
        onClick={onTapScore}
        className={`min-w-[70px] rounded-md border px-3 py-2 font-mono-num text-base ${
          filled
            ? "border-[var(--color-accent-cyan)] text-[var(--color-accent-cyan)]"
            : "border-[var(--color-border-subtle)] text-[var(--color-text-muted)]"
        }`}
      >
        {filled ? `${match.betScoreT1}-${match.betScoreT2}` : "_ - _"}
      </button>
      <div className="flex-1 text-left text-sm">
        {match.team2Name}
        <span className="ml-1">{match.team2Flag}</span>
      </div>
      <button
        type="button"
        onClick={onAskPaul}
        aria-label="Ask Paul"
        title="Ask Paul"
        className="flex h-9 w-9 items-center justify-center rounded-md bg-[var(--color-accent-purple)]/20 text-base hover:bg-[var(--color-accent-purple)]/40"
      >
        🐙
      </button>
    </div>
  );
}

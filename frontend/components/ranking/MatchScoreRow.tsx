import type { MatchScore, TeamRef } from "@/lib/api/scorecard";

type Labels = {
  pick: string;
  result: string;
  bdOutcome: string;
  bdTeam1: string;
  bdTeam2: string;
  bdDiff: string;
  multiplier: (n: number) => string;
  pts: (n: number) => string;
};

function team(t: TeamRef): string {
  return `${t.flag ?? ""} ${t.code ?? "—"}`.trim();
}

function score(t1: number | null, t2: number | null): string {
  return t1 === null || t2 === null ? "—" : `${t1}–${t2}`;
}

export function MatchScoreRow({ match, labels }: { match: MatchScore; labels: Labels }) {
  const b = match.breakdown;
  const parts: string[] = [];
  if (b.outcome) parts.push(`${labels.bdOutcome} ${labels.pts(b.outcome)}`);
  if (b.team1Exact) parts.push(`${labels.bdTeam1} ${labels.pts(b.team1Exact)}`);
  if (b.team2Exact) parts.push(`${labels.bdTeam2} ${labels.pts(b.team2Exact)}`);
  if (b.goalDiff) parts.push(`${labels.bdDiff} ${labels.pts(b.goalDiff)}`);
  if (b.multiplier > 1) parts.push(labels.multiplier(b.multiplier));

  return (
    <div className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2">
      <div className="flex items-baseline justify-between gap-2">
        <span className="truncate font-display text-sm font-extrabold uppercase tracking-tight">
          {team(match.team1)}–{team(match.team2)}
        </span>
        <span className="shrink-0 font-display text-base font-extrabold text-[var(--color-accent-red)]">
          {labels.pts(b.total)}
        </span>
      </div>
      <div className="mt-0.5 flex items-baseline justify-between gap-2 text-xs text-[var(--color-text-muted)]">
        <span>
          {labels.pick}: <span className="font-bold">{score(match.betScoreT1, match.betScoreT2)}</span>
        </span>
        <span>
          {labels.result}:{" "}
          <span className="font-bold">{score(match.actualScoreT1, match.actualScoreT2)}</span>
        </span>
      </div>
      {parts.length > 0 && (
        <div className="mt-1 font-mono text-[10px] uppercase tracking-[0.04em] text-[var(--color-text-muted)]">
          {parts.join(" · ")}
        </div>
      )}
    </div>
  );
}

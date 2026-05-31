import type { RankingEntry } from "@/lib/api/ranking";

/**
 * RankingRow — Estadio '26 poster row. Three cells:
 *   [rank + trend arrow] [name + optional YOU pill] [monospace points].
 * Trend column stays present even when delta is null so the column width
 * doesn't reflow as snapshots arrive in v1.1.
 */
export function RankingRow({
  entry,
  youLabel,
  trendUp,
  trendDown,
  trendFlat,
  payoutLabel,
}: {
  entry: RankingEntry;
  youLabel: string;
  trendUp: string;
  trendDown: string;
  trendFlat: string;
  /** Pre-formatted medal + payout string (e.g. "🥇 $24") for top-3 rows. */
  payoutLabel?: string;
}) {
  const delta = entry.delta;
  let trend = trendFlat;
  let trendTone = "text-[var(--color-text-muted)]";
  if (delta != null && delta > 0) {
    trend = trendUp;
    trendTone = "text-[var(--color-accent-green)]";
  } else if (delta != null && delta < 0) {
    trend = trendDown;
    trendTone = "text-[var(--color-accent-red)]";
  }

  const rowTone = entry.isYou
    ? "bg-[var(--color-bg-ink)] text-[var(--color-text-inverse)]"
    : "bg-[var(--color-bg-paper)]";

  return (
    <div
      className={`flex items-stretch border-[1.5px] border-[var(--color-line-ink)] ${rowTone}`}
    >
      {/* Rank + trend */}
      <div className="flex w-16 shrink-0 items-center justify-between border-r-[1.5px] border-[var(--color-line-ink)] px-2.5 py-2">
        <span className="font-display text-2xl font-black leading-none tracking-[-0.04em]">
          {entry.rank}
        </span>
        <span className={`font-mono text-xs font-bold ${trendTone}`}>{trend}</span>
      </div>

      {/* Name + optional YOU pill + optional medal/payout */}
      <div className="flex min-w-0 flex-1 items-center gap-2 px-3 py-2">
        <span className="truncate font-display text-base font-extrabold uppercase tracking-tight">
          {entry.displayName ?? "—"}
        </span>
        {entry.isYou && (
          <span className="shrink-0 bg-[var(--color-accent-gold)] px-1.5 py-0.5 font-mono text-[9px] font-bold tracking-[0.12em] text-[var(--color-text-primary)]">
            {youLabel}
          </span>
        )}
        {payoutLabel && (
          <span className="shrink-0 font-mono text-[11px] font-bold tracking-[0.04em] text-[var(--color-accent-gold)]">
            {payoutLabel}
          </span>
        )}
        {entry.isBot && (
          <span className="shrink-0 bg-[var(--color-accent-violet,#7c3aed)] px-1.5 py-0.5 font-mono text-[9px] font-bold tracking-[0.12em] text-[var(--color-text-inverse,#fff)]">
            FUERA DE PREMIO
          </span>
        )}
      </div>

      {/* Points — monospace, right-aligned, fixed-width column */}
      <div className="flex w-20 shrink-0 items-center justify-end border-l-[1.5px] border-[var(--color-line-ink)] px-3 py-2">
        <span
          className={`font-mono text-lg font-extrabold tabular-nums ${entry.isYou ? "text-[var(--color-accent-gold)]" : "text-[var(--color-text-primary)]"}`}
        >
          {entry.points}
        </span>
      </div>
    </div>
  );
}

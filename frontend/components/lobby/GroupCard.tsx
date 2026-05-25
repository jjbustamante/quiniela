import Link from "next/link";

export function GroupCard({
  letter,
  filled,
  total,
}: {
  letter: string;
  filled: number;
  total: number;
}) {
  const pct = total === 0 ? 0 : Math.round((filled / total) * 100);
  const complete = filled === total && total > 0;
  return (
    <Link
      href={`/group/${letter}`}
      className="flex items-center justify-between rounded-md border border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] px-4 py-3 transition-colors hover:border-[var(--color-border-accent)]/40"
    >
      <div>
        <div className="text-sm font-bold text-[var(--color-text-primary)]">Grupo {letter}</div>
        <div className="mt-0.5 text-xs text-[var(--color-text-muted)]">
          {filled} / {total}
        </div>
      </div>
      <div className="h-1.5 w-16 overflow-hidden rounded-full bg-[var(--color-border-subtle)]">
        <div
          className={`h-full ${complete ? "bg-[var(--color-state-good)]" : "bg-[var(--color-accent-cyan)]"}`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </Link>
  );
}

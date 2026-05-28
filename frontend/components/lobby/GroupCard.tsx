import Link from "next/link";

/**
 * GroupCard — poster tile with a big condensed letter, a status pill, and
 * the four team flags + codes underneath. Status pill: green (done), gold
 * (in progress), red (empty). Tap to drill into the group.
 */
export function GroupCard({
  letter,
  filled,
  total,
  teams,
  locked = false,
  lockedLabel,
}: {
  letter: string;
  filled: number;
  total: number;
  /** Pass {code, flag} per team if you have them; falls back to a row of
   *  empty placeholders that still preserves the card height. */
  teams?: ReadonlyArray<{ code: string; flag: string | null }>;
  locked?: boolean;
  lockedLabel?: string;
}) {
  const done = filled === total && total > 0;
  const empty = filled === 0;
  const pillBg = locked
    ? "bg-[var(--color-bg-ink)] text-[var(--color-accent-gold)]"
    : done
      ? "bg-[var(--color-accent-green)] text-[var(--color-text-inverse)]"
      : empty
        ? "bg-[var(--color-accent-red)] text-[var(--color-text-inverse)]"
        : "bg-[var(--color-accent-gold)] text-[var(--color-text-primary)]";

  const slots = teams ?? Array.from({ length: 4 }).map((_, i) => ({ code: `T${i + 1}`, flag: null }));

  return (
    <Link
      href={`/group/${letter}`}
      className="poster block bg-[var(--color-bg-paper)] p-2.5 transition-colors hover:bg-[#fff]"
    >
      <div className="flex items-center justify-between">
        <div className="font-display text-4xl font-black leading-[0.85] tracking-[-0.05em] text-[var(--color-text-primary)]">
          {letter}
        </div>
        <span className={`font-mono text-[10px] font-bold tracking-[0.08em] px-2 py-0.5 ${pillBg}`}>
          {locked ? (lockedLabel ?? "🔒") : `${filled}/${total}`}
        </span>
      </div>
      <div className="mt-2 flex items-center gap-0.5">
        {slots.slice(0, 4).map((t, i) => (
          <div key={`${t.code}-${i}`} className="flex min-w-0 flex-1 items-center gap-0.5">
            <span className="shrink-0 text-[12px] leading-none">{t.flag ?? "·"}</span>
            <span className="truncate font-mono text-[9px] font-bold tracking-[0.02em] text-[var(--color-text-muted)]">
              {t.code}
            </span>
          </div>
        ))}
      </div>
    </Link>
  );
}

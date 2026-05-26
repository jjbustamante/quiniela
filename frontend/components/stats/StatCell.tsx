/**
 * StatCell — one cell in a grouped stat strip (landing + home pages).
 *
 * Two sizes:
 *   - `lg`: hero-scale display (landing). Center-aligned, larger label, big numerals.
 *   - `md`: in-app stat strip (home). Left-aligned, default label, compact numerals.
 *
 * Three tones map to the brand palette (paper / gold / green). Label color
 * inherits from the cell's text color — keep `.chrome-label` color-free in
 * globals.css and this stays a one-prop call.
 *
 * The left divider is conditional via `not-first:` so a `<div class="grid">`
 * parent just lays cells out and they border themselves.
 */

type Tone = "paper" | "gold" | "green";
type Size = "md" | "lg";

const TONES: Record<Tone, string> = {
  paper: "bg-[var(--color-bg-paper)] text-[var(--color-text-primary)]",
  gold: "bg-[var(--color-accent-gold)] text-[var(--color-text-primary)]",
  green: "bg-[var(--color-accent-green)] text-[var(--color-text-inverse)]",
};

const SIZES: Record<
  Size,
  { wrapper: string; label: string; gap: string; value: string }
> = {
  md: {
    wrapper: "px-3 py-3",
    label: "chrome-label",
    gap: "mt-1",
    value: "text-2xl",
  },
  lg: {
    wrapper: "px-4 py-5 text-center",
    label: "chrome-label text-sm!",
    gap: "mt-2",
    value: "text-5xl sm:text-6xl",
  },
};

export function StatCell({
  label,
  value,
  tone,
  size = "md",
}: {
  label: string;
  value: string;
  tone: Tone;
  size?: Size;
}) {
  const s = SIZES[size];
  return (
    <div
      className={`${s.wrapper} ${TONES[tone]} not-first:border-l-[1.5px] not-first:border-[var(--color-line-ink)]`}
    >
      <div className={s.label}>{label}</div>
      <div
        className={`${s.gap} font-display font-black leading-none tracking-[-0.04em] ${s.value}`}
      >
        {value}
      </div>
    </div>
  );
}

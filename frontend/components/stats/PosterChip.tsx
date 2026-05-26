/**
 * PosterChip — value-plus-label chip used in the lobby's bottom strip
 * (countdown, pot, future status pills). Two tones from the brand palette;
 * the chip composes its own border and bg so callers don't repeat the
 * vocabulary.
 *
 * The label is rendered with `.chrome-label` but without the muted color
 * default, so the inline className (e.g. `text-[var(--color-accent-gold)]`)
 * controls the accent. Inherit-on-default keeps the chip readable on any
 * tone.
 */

type Tone = "ink" | "gold";

const TONES: Record<Tone, string> = {
  ink: "bg-[var(--color-bg-ink)] text-[var(--color-text-inverse)]",
  gold: "bg-[var(--color-accent-gold)] text-[var(--color-text-primary)]",
};

export function PosterChip({
  value,
  label,
  tone,
  labelClassName = "",
}: {
  value: string | number;
  label: string;
  tone: Tone;
  labelClassName?: string;
}) {
  return (
    <div
      className={`flex items-baseline gap-2 border-[1.5px] border-[var(--color-line-ink)] px-3 py-2 ${TONES[tone]}`}
    >
      <span className="font-display text-xl font-black leading-none tracking-[-0.04em]">
        {value}
      </span>
      <span className={`chrome-label ${labelClassName}`}>{label}</span>
    </div>
  );
}

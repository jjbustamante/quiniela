import Link from "next/link";
import { useTranslations } from "next-intl";
import type { PhaseChip } from "@/lib/home-phase";

const GLYPH: Record<PhaseChip["state"], string> = { done: "✓", open: "●", locked: "🔒" };

export function PhaseRail({ chips }: { chips: PhaseChip[] }) {
  const t = useTranslations("home");
  return (
    <section className="mx-3 mt-4">
      <div className="flex flex-wrap gap-1.5">
        {chips.map((c) => {
          const tone =
            c.state === "open"
              ? "bg-[var(--color-accent-gold)] text-[var(--color-text-primary)] border-[var(--color-line-ink)]"
              : c.state === "done"
                ? "bg-[var(--color-accent-green)] text-[var(--color-text-inverse)] border-[var(--color-accent-green)]"
                : "bg-[var(--color-bg-paper)] text-[var(--color-text-muted)] border-[#bbb]";
          return (
            <Link
              key={c.code}
              href={c.href}
              className={`border-[1.5px] px-2 py-1 font-display text-[10px] font-extrabold uppercase tracking-[0.04em] ${tone}`}
            >
              {t(`chip${c.code}` as never)} {GLYPH[c.state]}
            </Link>
          );
        })}
      </div>
    </section>
  );
}

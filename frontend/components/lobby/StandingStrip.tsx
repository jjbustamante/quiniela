import Link from "next/link";
import { useTranslations } from "next-intl";
import type { Standing } from "@/lib/home-phase";

const MEDAL: Record<number, string> = { 1: "🥇", 2: "🥈", 3: "🥉" };

export function StandingStrip({ standing }: { standing: Standing }) {
  const t = useTranslations("home");
  if (!standing) return null;
  const medal = standing.hasScored ? MEDAL[standing.rank] : undefined;

  return (
    <section className="mx-3 mt-4">
      <div className="chrome-label chrome-label-muted">{t("tuPuesto")}</div>
      <div className="mt-1 flex items-center justify-between border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5">
        <span className="font-display text-lg font-extrabold tracking-tight text-[var(--color-text-primary)]">
          {medal ? `${medal} ` : ""}#{standing.rank} · {standing.points} pts
        </span>
        <Link href="/ranking" className="chrome-label text-[var(--color-accent-red)]">
          {t("verTabla")}
        </Link>
      </div>
    </section>
  );
}

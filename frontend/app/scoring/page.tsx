import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getPublicSummaryOrFallback } from "@/lib/api/summary";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";

/**
 * Scoring explainer — a static, everyone-can-read page describing the additive
 * point model (see backend V010/V016 migrations, the source of truth). Reached
 * from the NavDrawer. Group-stage values shown; knockouts double them.
 */
export default async function ScoringPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const t = await getTranslations("scoring");
  const summary = await getPublicSummaryOrFallback();

  const components: { label: string; note?: string; n: number }[] = [
    { label: t("ptsOutcome"), n: 3 },
    { label: t("ptsTeam"), n: 2 },
    { label: t("ptsOtherTeam"), n: 2 },
    { label: t("ptsDiff"), note: t("ptsDiffNote"), n: 1 },
  ];

  // Worked examples against a real 2–1 result.
  const examples: { pred: string; desc: string; n: number }[] = [
    { pred: "2–1", desc: t("exExact"), n: 7 },
    { pred: "2–0", desc: t("exTeam"), n: 5 },
    { pred: "3–2", desc: t("exDiff"), n: 4 },
    { pred: "0–1", desc: t("exMiss"), n: 0 },
  ];

  const sectionClass =
    "border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4";
  const headClass = "chrome-label chrome-label-muted mb-2";

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} />
      <div className="mx-auto flex w-full max-w-md flex-col gap-3 px-3 pt-4 sm:max-w-2xl">
        <p className="font-sans text-sm text-[var(--color-text-muted)]">{t("intro")}</p>

        <section className={sectionClass}>
          <div className={headClass}>{t("perMatch")}</div>
          <ul className="flex flex-col gap-2">
            {components.map((c) => (
              <li key={c.label} className="flex items-baseline justify-between gap-3">
                <span className="font-sans text-sm text-[var(--color-text-primary)]">
                  {c.label}
                  {c.note && (
                    <span className="block text-xs text-[var(--color-text-muted)]">{c.note}</span>
                  )}
                </span>
                <span className="shrink-0 font-display text-base font-extrabold text-[var(--color-accent-red)]">
                  {t("pts", { n: c.n })}
                </span>
              </li>
            ))}
          </ul>
        </section>

        <section className={sectionClass}>
          <div className={headClass}>{t("exactTitle")}</div>
          <p className="font-sans text-sm text-[var(--color-text-primary)]">{t("exactBody")}</p>
        </section>

        <section className={sectionClass}>
          <div className={headClass}>{t("multipliersTitle")}</div>
          <p className="mb-2 font-sans text-sm text-[var(--color-text-primary)]">
            {t("multipliersIntro")}
          </p>
          <ul className="flex flex-col gap-2">
            {summary.roundMultipliers.map((r) => (
              <li key={r.code} className="flex items-baseline justify-between gap-3">
                <span className="font-sans text-sm text-[var(--color-text-primary)]">{r.name}</span>
                <span className="shrink-0 font-display text-base font-extrabold text-[var(--color-accent-red)]">
                  ×{r.multiplier}
                </span>
              </li>
            ))}
          </ul>
        </section>

        <section className={sectionClass}>
          <div className={headClass}>{t("drawTitle")}</div>
          <p className="font-sans text-sm text-[var(--color-text-primary)]">{t("drawBody")}</p>
        </section>

        <section className={sectionClass}>
          <div className={headClass}>{t("examplesTitle")}</div>
          <p className="mb-3 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-primary)]">
            {t("examplesActual")}
          </p>
          <ul className="flex flex-col gap-2">
            {examples.map((e) => (
              <li
                key={e.pred}
                className="flex items-baseline justify-between gap-3 border-t-[1.5px] border-dashed border-[var(--color-line-ink)] pt-2 first:border-t-0 first:pt-0"
              >
                <span className="font-sans text-sm text-[var(--color-text-primary)]">
                  <span className="font-display font-extrabold">{e.pred}</span>
                  <span className="block text-xs text-[var(--color-text-muted)]">{e.desc}</span>
                </span>
                <span className="shrink-0 font-display text-base font-extrabold text-[var(--color-accent-red)]">
                  {t("pts", { n: e.n })}
                </span>
              </li>
            ))}
          </ul>
        </section>
      </div>
      <BottomNav />
    </main>
  );
}

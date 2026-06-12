import Link from "next/link";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getScorecard } from "@/lib/api/scorecard";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { StageSection } from "@/components/shared/StageSection";
import { MatchScoreRow } from "@/components/ranking/MatchScoreRow";

export default async function ScorecardPage({
  params,
}: {
  params: Promise<{ userId: string }>;
}) {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const { userId } = await params;
  const card = await getScorecard(Number(userId));
  const t = await getTranslations("scorecard");
  const tRound = await getTranslations("home");

  // Most-recent stage first (backend returns round-sequence ascending).
  const stages = [...card.stages].reverse();

  const rowLabels = {
    pick: t("pick"),
    result: t("result"),
    bdOutcome: t("bdOutcome"),
    bdTeam1: t("bdTeam1"),
    bdTeam2: t("bdTeam2"),
    bdDiff: t("bdDiff"),
    multiplier: (n: number) => t("multiplier", { n }),
    pts: (n: number) => t("pts", { n }),
    noteText: (key: 'PLACED_AFTER_KICKOFF' | 'EDITED_AFTER_KICKOFF') => t(key),
  };

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={(card.displayName ?? "—").toUpperCase()} meta={t("totalPoints", { n: card.totalPoints })} />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="px-3 pt-3">
          <Link
            href="/ranking"
            className="chrome-label inline-flex items-center gap-1 text-[var(--color-text-primary)] hover:text-[var(--color-accent-red)]"
          >
            ← {t("backToTable")}
          </Link>
        </div>

        <div className="mx-3 mt-2 flex items-baseline justify-between gap-3 border-b-[1.5px] border-[var(--color-line-ink)] pb-2">
          <h1 className="truncate font-display text-2xl font-black uppercase tracking-[-0.03em]">
            {card.displayName ?? "—"}
          </h1>
          <span className="shrink-0 font-display text-lg font-extrabold text-[var(--color-accent-red)]">
            {t("totalPoints", { n: card.totalPoints })}
          </span>
        </div>

        {stages.length === 0 ? (
          <section className="mx-3 mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center">
            <p className="font-display text-base font-extrabold uppercase tracking-tight text-[var(--color-text-muted)]">
              {t("noPoints")}
            </p>
          </section>
        ) : (
          <section className="mx-3 mt-3 flex flex-col gap-2">
            {stages.map((s, i) => (
              <StageSection
                key={s.roundCode}
                header={tRound(`chip${s.roundCode}` as never)}
                count={s.points}
                defaultOpen={i === 0}
              >
                {s.matches.map((m) => (
                  <MatchScoreRow key={m.matchId} match={m} labels={rowLabels} />
                ))}
              </StageSection>
            ))}
          </section>
        )}
      </div>

      <BottomNav activeKey="ranking" />
    </main>
  );
}

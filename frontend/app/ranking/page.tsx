import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getRanking } from "@/lib/api/ranking";
import { getPublicSummary } from "@/lib/api/summary";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { RankingRow } from "@/components/ranking/RankingRow";
import { deadlineShort } from "@/lib/tournament-format";
import { buildPayoutLabels } from "@/lib/ranking-payouts";

export default async function RankingPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const [me, ranking, summary] = await Promise.all([getMe(), getRanking(), getPublicSummary()]);
  const tNav = await getTranslations("nav");
  const t = await getTranslations("ranking");

  const updatedLabel = deadlineShort(ranking.updatedAt, me.timezone);
  const count = ranking.entries.length;

  // Medal + payout labels for the top-3 ranks. Suppressed entirely until
  // someone has scored, and reduced to a bare medal on a tie (the split prize
  // shouldn't render as the full amount next to each tied player).
  const payoutByRank = buildPayoutLabels(
    ranking.entries,
    summary.prizeSplit,
    summary.pool.currency,
  );

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={tNav("ranking").toUpperCase()} meta={`${count}`} />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="px-3 pt-3">
          <span className="chrome-label chrome-label-muted">
            {t("subtitle", { count, when: updatedLabel })}
          </span>
        </div>

        {count === 0 ? (
          <section className="mx-3 mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center">
            <p className="font-display text-base font-extrabold uppercase tracking-tight text-[var(--color-text-muted)]">
              {t("empty")}
            </p>
          </section>
        ) : (
          <section className="mx-3 mt-3 flex flex-col gap-1.5">
            {ranking.entries.some((e) => e.isBot) && (
              <div className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-4 py-2 text-sm font-semibold text-[var(--color-text-primary)]">
                🐙 ¡Pulpo Paul decidió jugar! Compite por la gloria, no por el premio.
              </div>
            )}
            <div className="flex items-stretch border-b-[1.5px] border-[var(--color-line-ink)] px-1 py-1">
              <span className="chrome-label chrome-label-muted w-16 shrink-0 px-2.5">
                {t("headerRank")}
              </span>
              <span className="chrome-label chrome-label-muted flex-1 px-3">
                {t("headerName")}
              </span>
              <span className="chrome-label chrome-label-muted w-20 shrink-0 px-3 text-right">
                {t("headerPoints")}
              </span>
            </div>
            {ranking.entries.map((e) => (
              <RankingRow
                key={e.userId}
                entry={e}
                youLabel={t("you")}
                trendUp={t("trendUp")}
                trendDown={t("trendDown")}
                trendFlat={t("trendFlat")}
                payoutLabel={payoutByRank.get(e.rank)}
              />
            ))}
          </section>
        )}
      </div>

      <BottomNav activeKey="ranking" />
    </main>
  );
}

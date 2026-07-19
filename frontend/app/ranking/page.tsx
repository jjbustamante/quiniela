import Link from "next/link";
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

  // Medal + payout labels for the top-3 prize-eligible players, keyed by userId
  // (not rank — a bot can occupy a raw rank without being prize-eligible).
  // Suppressed entirely until someone has scored, and reduced to a bare medal
  // on a tie (the split prize shouldn't render as the full amount next to each
  // tied player).
  const payoutByUserId = buildPayoutLabels(
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

        {ranking.liveScoring && (
          <div className="mx-3 mt-2 inline-flex items-center gap-1.5 border-[1.5px] border-[var(--color-accent-red)] px-3 py-1.5 font-mono text-[10px] font-bold uppercase tracking-[0.12em] text-[var(--color-accent-red)]">
            <span className="inline-block h-2 w-2 rounded-full bg-[var(--color-accent-red)]" />
            {t("liveBanner")}
          </div>
        )}

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
                {t("botsAnnouncement")}
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
              <Link key={e.userId} href={`/ranking/${e.userId}`} className="block">
                <RankingRow
                  entry={e}
                  youLabel={t("you")}
                  trendUp={t("trendUp")}
                  trendDown={t("trendDown")}
                  trendFlat={t("trendFlat")}
                  payoutLabel={payoutByUserId.get(e.userId)}
                />
              </Link>
            ))}
          </section>
        )}
      </div>

      <BottomNav activeKey="ranking" />
    </main>
  );
}

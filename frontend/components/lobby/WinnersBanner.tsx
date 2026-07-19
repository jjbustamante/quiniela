import { useTranslations } from "next-intl";
import type { Winners } from "@/lib/home-phase";

const MEDAL: Record<number, string> = { 1: "🥇", 2: "🥈", 3: "🥉" };

export function WinnersBanner({ winners }: { winners: Winners }) {
  const t = useTranslations("home");
  if (!winners) return null;

  return (
    <section className="mx-3 mt-4 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4">
      <div className="chrome-label chrome-label-muted">{t("winnersPodiumTitle")}</div>
      <div className="mt-2 font-display text-base font-extrabold text-[var(--color-text-primary)]">
        {winners.overall.isBot
          ? t("winnersOverallBotHeadline", { name: winners.overall.displayName ?? "" })
          : t("winnersOverallHumanHeadline", { name: winners.overall.displayName ?? "" })}
      </div>
      <div className="chrome-label mt-1 text-[var(--color-text-secondary)]">
        {winners.overall.isBot
          ? t("winnersOverallBotSub", { points: winners.overall.points })
          : t("winnersOverallHumanSub", { points: winners.overall.points })}
      </div>
      <ul className="mt-3 space-y-1.5">
        {winners.prizeTop.map((g) => {
          const medal = MEDAL[g.rank];
          // prizeSplit is admin-configurable (PoolConfigPanel's "add rank" has no cap) —
          // a 4th+ tier would have no medal. Skip rather than render "undefined {name}".
          if (!medal) return null;
          const names = g.winners.map((w) => w.displayName ?? "?").join(" & ");
          const amount = `$${(g.payoutCentsEach / 100).toFixed(0)}`;
          return (
            <li key={`prize-${g.rank}`} className="font-display text-sm font-bold text-[var(--color-text-primary)]">
              {g.winners.length > 1
                ? t("winnersPodiumRowTied", { medal, names, points: g.winners[0].points, amount })
                : t("winnersPodiumRowSingle", { medal, name: names, points: g.winners[0].points, amount })}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

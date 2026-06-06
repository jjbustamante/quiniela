import Link from "next/link";
import { useTranslations } from "next-intl";
import type { Recap } from "@/lib/home-phase";
import { formatMatchDateTime } from "@/lib/format-datetime";
import { formatPot, daysUntil } from "@/lib/tournament-format";

export function ResultsRecap({ recap, timeZone }: { recap: Recap; timeZone: string }) {
  const t = useTranslations("home");

  if (recap.kind === "preKickoff") {
    return (
      <section className="mx-3 mt-4">
        <div className="flex items-center justify-between border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5 font-display text-sm font-extrabold text-[var(--color-text-primary)]">
          <span>{t("preKickoffPot", { amount: formatPot(recap.potCents, recap.currency), panas: recap.panaCount })}</span>
          <span className="text-[var(--color-accent-red)]">{t("preKickoffCountdown", { days: daysUntil(recap.startDate) })}</span>
        </div>
      </section>
    );
  }

  return (
    <section className="mx-3 mt-4">
      <div className="flex items-baseline justify-between">
        <span className="chrome-label chrome-label-muted">{t("resultadosProximos")}</span>
        <Link href="/matches" className="chrome-label text-[var(--color-accent-red)]">{t("verPartidos")}</Link>
      </div>
      <div className="mt-1 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)]">
        {recap.recent.map((m) => (
          <div key={m.matchId} className="flex items-center justify-between border-b border-dashed border-[#ccc] px-3 py-2 text-sm font-bold last:border-b-0">
            <span>
              <span className="chrome-label chrome-label-muted mr-1.5">{t("tagFinal")}</span>
              {m.t1Flag} {m.t1Code} {m.s1}–{m.s2} {m.t2Code} {m.t2Flag}
            </span>
            {m.pointsEarned != null && (
              <span className="font-display font-black text-[var(--color-accent-green)]">+{m.pointsEarned}</span>
            )}
          </div>
        ))}
        {recap.next && (
          <div className="flex items-center justify-between bg-[#faf7ef] px-3 py-2 text-sm font-bold">
            <span>
              <span className="chrome-label chrome-label-muted mr-1.5">{formatMatchDateTime(recap.next.kickoffAt, timeZone)}</span>
              {recap.next.t1Flag} {recap.next.t1Code} – {recap.next.t2Code} {recap.next.t2Flag}
            </span>
            <span className="text-[var(--color-text-muted)]">·</span>
          </div>
        )}
      </div>
    </section>
  );
}

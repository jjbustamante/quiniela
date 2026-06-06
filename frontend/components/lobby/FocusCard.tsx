import Link from "next/link";
import { useTranslations } from "next-intl";
import type { FocusState } from "@/lib/home-phase";
import { deadlineShort } from "@/lib/tournament-format";

function Shell({ kicker, headline, sub, cta, ghost }: { kicker: string; headline: string; sub?: string; cta?: { label: string; href: string }; ghost?: string }) {
  return (
    <section className="relative mx-3 mt-4 overflow-hidden bg-[var(--color-bg-ink)] p-4 text-[var(--color-text-inverse)]">
      {ghost && (
        <div aria-hidden="true" className="pointer-events-none absolute right-[-8px] top-[-16px] font-display text-[120px] font-black leading-none tracking-[-0.08em] text-[var(--color-accent-red)] opacity-90">{ghost}</div>
      )}
      <div className="relative">
        <span className="chrome-label text-[var(--color-accent-gold)]">{kicker}</span>
        <div className="mt-1 font-display text-[28px] font-black uppercase leading-[0.95] tracking-[-0.03em]">{headline}</div>
        {sub && <div className="chrome-label mt-2 text-white/70">{sub}</div>}
        {cta && (
          <Link href={cta.href} className="mt-3 block bg-[var(--color-accent-red)] px-4 py-2.5 text-center font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-gold)] hover:text-[var(--color-text-primary)]">
            {cta.label}
          </Link>
        )}
      </div>
    </section>
  );
}

export function FocusCard({ focus, timeZone }: { focus: FocusState; timeZone: string }) {
  const t = useTranslations("home");

  switch (focus.kind) {
    case "fillGroup": {
      const when = focus.deadline ? deadlineShort(focus.deadline, timeZone) : "";
      if (focus.full) {
        return <Shell kicker={t("fillDoneKicker")} headline={t("fillGroupDoneHeadline")} sub={t("fillDoneSub", { when })} />;
      }
      return <Shell kicker={t("fillGroupKicker", { when })} headline={t("fillGroupHeadline")} sub={t("fillSub", { filled: focus.filled, total: focus.total })} cta={{ label: focus.filled === 0 ? t("fillGroupCtaEmpty") : t("fillGroupCta"), href: focus.href }} />;
    }
    case "fillKnockout": {
      const when = focus.deadline ? deadlineShort(focus.deadline, timeZone) : "";
      if (focus.full) {
        return <Shell kicker={t("fillKnockoutKicker", { when })} headline={t("fillKnockoutDoneHeadline", { round: focus.roundName })} sub={t("fillSub", { filled: focus.filled, total: focus.total })} />;
      }
      return <Shell kicker={t("fillKnockoutKicker", { when })} headline={t("fillKnockoutHeadline", { round: focus.roundName })} sub={t("fillSub", { filled: focus.filled, total: focus.total })} cta={{ label: t("fillKnockoutCta", { round: focus.roundName }), href: focus.href }} />;
    }
    case "live": {
      const phase = focus.phase === "group" ? t("livePhaseGroup") : t("livePhaseKnockout");
      const sub = focus.rank != null ? t("liveSub", { rank: focus.rank, points: focus.points ?? 0 }) : t("liveSubNoScore");
      return <Shell kicker={phase} headline={t("liveHeadline")} sub={sub} />;
    }
    case "champion": {
      const headline = focus.rank != null ? t("championHeadline", { rank: focus.rank }) : t("liveHeadline");
      const sub = focus.payoutCents != null
        ? t("championSubPayout", { points: focus.points ?? 0, amount: `$${(focus.payoutCents / 100).toFixed(0)}` })
        : t("championSub", { points: focus.points ?? 0 });
      return <Shell kicker={t("championKicker")} headline={headline} sub={sub} ghost="🏆" />;
    }
  }
}

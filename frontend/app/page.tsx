import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { AuthButton } from "@/components/AuthButton";
import { PaulBadge } from "@/components/PaulMascot";
import { StatCell } from "@/components/stats/StatCell";
import { getPublicSummary } from "@/lib/api/summary";
import {
  dateRangeShort,
  daysUntil,
  hostLine,
  yearGlyph,
} from "@/lib/tournament-format";

/**
 * Landing page — public, unauthed. Sells the quiniela in one screen.
 * Sign-in works for anyone with Google; the auth callback routes
 * uninvited accounts to /auth-error. This page sets that expectation
 * up-front so people don't sign in blind.
 */
export default async function Home() {
  const session = await auth();
  if (session?.userId) redirect("/home");

  const [t, tCommon, summary] = await Promise.all([
    getTranslations("landing"),
    getTranslations("common"),
    getPublicSummary(),
  ]);

  const { tournament, pool } = summary;
  const days = daysUntil(tournament.startDate);
  const hosts = hostLine(tournament.hostCountryCodes, tournament.startDate);

  return (
    <main className="flex min-h-screen flex-col bg-[var(--color-bg-primary)]">
      {/* Masthead */}
      <header className="bg-[var(--color-bg-ink)] text-[var(--color-text-inverse)] flex items-center justify-between px-5 py-4">
        <div className="flex items-center gap-3">
          <PaulBadge size={36} />
          <div className="font-display text-base font-extrabold uppercase leading-[0.9] tracking-tight">
            Quiniela<br />Panas
          </div>
        </div>
        <span className="chrome-label text-[var(--color-accent-gold)]">{t("established")}</span>
      </header>

      {/* Host-country stripe */}
      <div className="stripe-host">
        <div /><div /><div />
      </div>

      <section className="relative mx-auto flex w-full max-w-md flex-1 flex-col px-5 py-6 sm:max-w-2xl sm:py-10 lg:max-w-3xl">
        {/* Background ghost numeral — last two digits of the tournament year */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute right-[-50px] top-20 select-none font-display text-[380px] font-black leading-[0.85] tracking-[-0.08em] text-[var(--color-bg-ink)] opacity-[0.06]"
        >
          {yearGlyph(tournament.startDate)}
        </div>

        {/* Hero */}
        <div className="relative">
          <p className="chrome-label text-[var(--color-accent-red)]">
            {t("kicker", { days })}
          </p>
          <h1 className="headline-display mt-2 text-[56px] sm:text-7xl">
            {t("headlinePart1")}
            <br />
            <span className="text-[var(--color-accent-red)]">{t("headlineAccent")}</span>
            <br />
            {t("headlinePart2")}
          </h1>
          <p className="mt-5 max-w-md font-sans text-base leading-relaxed text-[var(--color-text-muted)]">
            {t.rich("body", {
              hosts,
              matches: tournament.totalGroupStageMatches,
              strong: (chunks) => (
                <strong className="font-semibold text-[var(--color-text-primary)]">{chunks}</strong>
              ),
            })}
          </p>
        </div>

        {/* Live stats poster — 2-up: PANAS + countdown to kickoff. Pot is intentionally
            withheld from the public landing page; it shows up on the home page after
            sign-in. */}
        <div className="relative mt-6 grid grid-cols-2 border-[1.5px] border-[var(--color-line-ink)]">
          <StatCell label={t("statPanas")} value={String(pool.panaCount)} tone="gold" size="lg" />
          <StatCell label={t("statKickoff")} value={t("kickoffDays", { days })} tone="green" size="lg" />
        </div>

        <div className="flex-1 min-h-4" />

        {/* CTA + invite-only nudge */}
        <div className="relative">
          <AuthButton />

          <div className="mt-3 flex items-start gap-3 border-[1.5px] border-dashed border-[var(--color-line-ink)] p-3">
            <div className="font-display text-2xl font-black leading-none text-[var(--color-accent-red)]">!</div>
            <p className="font-sans text-xs leading-relaxed text-[var(--color-text-primary)]">
              <span className="font-bold">{t("inviteOnlyTitle")}</span>{" "}
              <span className="text-[var(--color-text-muted)]">{t("inviteOnlyBody")}</span>
            </p>
          </div>

          <div className="mt-4 flex justify-between border-t border-[var(--color-line-subtle)] pt-3">
            <span className="chrome-label chrome-label-muted">{hosts}</span>
            <span className="chrome-label chrome-label-muted">
              {dateRangeShort(tournament.startDate, tournament.endDate)}
            </span>
          </div>
        </div>

        <span className="sr-only">{tCommon("appName")}</span>
      </section>
    </main>
  );
}

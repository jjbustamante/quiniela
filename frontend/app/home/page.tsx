import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getMyBracket } from "@/lib/api/bracket";
import { getPublicSummary } from "@/lib/api/summary";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { CountdownChip } from "@/components/lobby/CountdownChip";
import { PotChip } from "@/components/lobby/PotChip";
import { GroupCard } from "@/components/lobby/GroupCard";
import { KnockoutLockedCard } from "@/components/lobby/KnockoutLockedCard";
import { PaulFillAllButton } from "@/components/lobby/PaulFillAllButton";
import { InviteFriendsButton } from "@/components/invite/InviteFriendsButton";
import { StatCell } from "@/components/stats/StatCell";
import { dateLong, daysUntil, deadlineShort, formatPot } from "@/lib/tournament-format";

export default async function HomePage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const [me, bracket, summary] = await Promise.all([
    getMe(),
    getMyBracket(),
    getPublicSummary(),
  ]);
  const t = await getTranslations("lobby");

  const { tournament, pool } = summary;
  const days = daysUntil(tournament.startDate);
  const pot = formatPot(pool.potCents, pool.currency);
  const kickoffMeta = t("countdownMeta", {
    date: dateLong(tournament.startDate),
    venue: tournament.openingVenue ?? "",
  });

  const knockoutsUnlocked = bracket.knockouts.some((k) => k.unlocked);
  const pct = bracket.totalMatches
    ? Math.round((bracket.totalBets / bracket.totalMatches) * 100)
    : 0;
  const groupLockedLabel = bracket.groupStageDeadline
    ? t("groupLockedPill", { when: deadlineShort(bracket.groupStageDeadline, me.timezone) })
    : "🔒";

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar
        title={t("title")}
        meta={`${me.displayName.toUpperCase()} · ${bracket.totalBets}/${bracket.totalMatches}`}
      />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        {/* Hero — black countdown poster with ghost numeral */}
        <section className="relative mx-3 mt-4 overflow-hidden bg-[var(--color-bg-ink)] p-4 text-[var(--color-text-inverse)]">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute right-[-10px] top-[-20px] font-display text-[200px] font-black leading-none tracking-[-0.08em] text-[var(--color-accent-red)] opacity-90"
          >
            {days}
          </div>
          <div className="relative">
            <span className="chrome-label text-[var(--color-accent-gold)]">
              {t("countdownTitle")}
            </span>
            <div className="mt-1 font-display text-[40px] font-black uppercase leading-[0.95] tracking-[-0.03em]">
              {t("countdownHeadline")}
            </div>
            <div className="chrome-label mt-3 text-white/70">{kickoffMeta}</div>
          </div>
        </section>

        {/* Stat strip */}
        <section className="mx-3 mt-3 grid grid-cols-3 border-[1.5px] border-[var(--color-line-ink)]">
          <StatCell label={t("statPot")} value={pot} tone="paper" />
          <StatCell label={t("statPaid")} value={String(pool.panaCount)} tone="gold" />
          <StatCell label={t("statProgress")} value={`${pct}%`} tone="green" />
        </section>

        {/* Groups */}
        <section className="mx-3 mt-5">
          <div className="flex items-baseline justify-between">
            <h2 className="font-display text-xl font-extrabold uppercase tracking-tight">
              {t("groupsHeading")}
            </h2>
            <span className="chrome-label chrome-label-muted">{bracket.groups.length} {t("groupsCount")}</span>
          </div>
          <div className="mt-2.5 grid grid-cols-2 gap-2">
            {bracket.groups.map((g) => (
              <GroupCard
                key={g.code}
                letter={g.code}
                filled={g.filled}
                total={g.total}
                teams={teamPreview(g.matches)}
                locked={g.locked}
                lockedLabel={groupLockedLabel}
              />
            ))}
          </div>
        </section>

        {/* Knockouts */}
        <section className="mx-3 mt-5">
          <span className="chrome-label chrome-label-muted">{t("knockoutsHeading")}</span>
          <div className="mt-2">
            {knockoutsUnlocked ? (
              <div className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4 text-sm text-[var(--color-text-primary)]">
                {t("knockoutsOpen")}
              </div>
            ) : (
              <KnockoutLockedCard />
            )}
          </div>
        </section>

        {/* Action row */}
        <section className="mx-3 mt-4 flex flex-col gap-2 sm:flex-row">
          <div className="flex-1"><PaulFillAllButton /></div>
          <InviteFriendsButton role={me.role} invitePath={me.invitePath} />
        </section>

        {/* Live chips — pinned at the bottom of the lobby content so the user
            can always see countdown + pot at a glance without scrolling. */}
        <section className="mx-3 mt-5 flex flex-wrap gap-2">
          <CountdownChip kickoffDate={tournament.startDate} />
          <PotChip pot={pot} panaCount={pool.panaCount} />
        </section>
      </div>

      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

/** Build a 4-team preview from a group's matches (each group has the same
 *  4 teams across its 6 round-robin matches). Picks unique team codes
 *  in the order they appear. */
function teamPreview(matches: ReadonlyArray<{
  team1Code: string | null; team1Flag: string | null;
  team2Code: string | null; team2Flag: string | null;
}>) {
  const seen = new Map<string, { code: string; flag: string | null }>();
  for (const m of matches) {
    if (m.team1Code && !seen.has(m.team1Code)) seen.set(m.team1Code, { code: m.team1Code, flag: m.team1Flag });
    if (m.team2Code && !seen.has(m.team2Code)) seen.set(m.team2Code, { code: m.team2Code, flag: m.team2Flag });
    if (seen.size === 4) break;
  }
  return Array.from(seen.values()).slice(0, 4);
}

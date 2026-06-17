"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import type { H2HView, H2HMatch } from "@/lib/api/compare";
import { groupMatchesByStage } from "@/lib/matches-by-stage";
import { StageSection } from "@/components/shared/StageSection";

type Tab = "past" | "today" | "upcoming";
type Mode = "date" | "stage";

function teamLabel(flag: string | null, code: string | null): string {
  return `${flag ?? ""} ${code ?? "—"}`.trim();
}

function score(t1: number | null, t2: number | null): string {
  return t1 === null || t2 === null ? "—" : `${t1}–${t2}`;
}

/** differ-first ordering within any bucket */
function difFirst(list: H2HMatch[]): H2HMatch[] {
  const differ = list.filter((m) => m.state === "differ");
  const agree = list.filter((m) => m.state === "agree");
  return [...differ, ...agree];
}

export function H2HCompare({ data }: { data: H2HView | null }) {
  const t = useTranslations("compare");
  const tRound = useTranslations("home");

  // Hooks must come before any conditional return.
  // Prefer the first bucket that has REVEALED matches so we don't default onto an all-hidden Today.
  const initial: Tab =
    data && data.today.some((m) => m.revealed) ? "today"
    : data && data.upcoming.some((m) => m.revealed) ? "upcoming"
    : "past";
  const [mode, setMode] = useState<Mode>("date");
  const [tab, setTab] = useState<Tab>(initial);

  if (!data) {
    return (
      <section className="mx-3 mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] p-6 text-center">
        <p className="font-display text-base font-extrabold uppercase text-[var(--color-text-muted)]">
          {t("noRival")}
        </p>
      </section>
    );
  }

  const all = [...data.past, ...data.today, ...data.upcoming];
  const revealed = all.filter((m) => m.revealed);

  if (revealed.length === 0) {
    return (
      <section className="mx-auto flex w-full max-w-md flex-1 flex-col items-start justify-center gap-4 px-6 py-16">
        <h1 className="headline-display whitespace-pre-line text-[44px] sm:text-6xl">{t("lockedTitle")}</h1>
        <p className="font-sans text-base text-[var(--color-text-muted)]">{t("lockedHelp")}</p>
      </section>
    );
  }

  const points =
    data.myPoints !== null && data.rivalPoints !== null
      ? `${data.myPoints}–${data.rivalPoints}`
      : null;

  const rival = data.rivalDisplayName ?? `#${data.rivalUserId}`;

  // Derive per-bucket revealed lists so hidden matches don't inflate badge counts
  // or create header-only empty tables when an entire bucket is unrevealed.
  const pastR = data.past.filter((m) => m.revealed);
  const todayR = data.today.filter((m) => m.revealed);
  const upcomingR = data.upcoming.filter((m) => m.revealed);

  const bucketList = tab === "past" ? pastR : tab === "today" ? todayR : upcomingR;
  const emptyLabel = tab === "past" ? t("emptyPast") : tab === "today" ? t("emptyToday") : t("emptyUpcoming");

  return (
    <div className="mx-3 mt-2 flex flex-col gap-2">
      <p className="px-1 py-2 text-xs font-semibold text-[var(--color-text-muted)]">
        {points
          ? t("summaryWinning", { agree: data.agreeCount, differ: data.differCount, points })
          : t("summary", { agree: data.agreeCount, differ: data.differCount })}
      </p>

      <div className="mb-3 flex gap-1.5">
        <ModeButton active={mode === "date"} onClick={() => setMode("date")}>{t("viewByDate")}</ModeButton>
        <ModeButton active={mode === "stage"} onClick={() => setMode("stage")}>{t("viewByStage")}</ModeButton>
      </div>

      {mode === "date" ? (
        <>
          <div className="flex gap-1 border-b-[1.5px] border-[var(--color-line-ink)] px-1">
            <TabButton active={tab === "past"} onClick={() => setTab("past")}>{t("tabPast")} · {pastR.length}</TabButton>
            <TabButton active={tab === "today"} onClick={() => setTab("today")}>{t("tabToday")} · {todayR.length}</TabButton>
            <TabButton active={tab === "upcoming"} onClick={() => setTab("upcoming")}>{t("tabUpcoming")} · {upcomingR.length}</TabButton>
          </div>
          {bucketList.length === 0 ? (
            <div className="mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center">
              <p className="font-display text-base font-extrabold uppercase tracking-tight text-[var(--color-text-muted)]">{emptyLabel}</p>
            </div>
          ) : (
            <table className="w-full table-fixed border-collapse">
              <thead>
                <tr className="border-b-[1.5px] border-[var(--color-line-ink)] text-[9px] uppercase text-[var(--color-text-muted)]">
                  <th scope="col" className="w-[40%] py-1.5 text-left">{t("colMatch")}</th>
                  <th scope="col" className="py-1.5">{t("colYou")}</th>
                  <th scope="col" className="py-1.5">{rival}</th>
                  <th scope="col" className="py-1.5">{t("colReal")}</th>
                </tr>
              </thead>
              <tbody>
                {difFirst(bucketList).map((m) => (
                  <Row key={m.matchId} m={m} highlight={m.state === "differ"} />
                ))}
              </tbody>
            </table>
          )}
        </>
      ) : (
        <div className="flex flex-col gap-2">
          {/* Compare only shows revealed matches; treat all as "started" so stages order by
              phase recency (later rounds first). MAX_SAFE_INTEGER keeps render pure — mirrors
              GroupConsensus convention (no impure Date.now() in render). */}
          {groupMatchesByStage(revealed, Number.MAX_SAFE_INTEGER).map((g, i) => {
            const rows = difFirst(g.matches);
            return (
              <StageSection
                key={g.roundCode}
                header={tRound(`chip${g.roundCode}` as never)}
                count={g.matches.length}
                defaultOpen={i === 0}
              >
                <table className="w-full table-fixed border-collapse">
                  <thead>
                    <tr className="border-b-[1.5px] border-[var(--color-line-ink)] text-[9px] uppercase text-[var(--color-text-muted)]">
                      <th scope="col" className="w-[40%] py-1.5 text-left">{t("colMatch")}</th>
                      <th scope="col" className="py-1.5">{t("colYou")}</th>
                      <th scope="col" className="py-1.5">{rival}</th>
                      <th scope="col" className="py-1.5">{t("colReal")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((m) => (
                      <Row key={m.matchId} m={m} highlight={m.state === "differ"} />
                    ))}
                  </tbody>
                </table>
              </StageSection>
            );
          })}
        </div>
      )}
    </div>
  );
}

function Row({ m, highlight }: { m: H2HMatch; highlight: boolean }) {
  return (
    <tr
      className={`border-b border-[var(--color-line-soft,#e7ddcc)] text-xs ${highlight ? "bg-[#fff4d6]" : "opacity-60"}`}
    >
      <td className="py-2 text-left font-extrabold">
        {teamLabel(m.team1Flag, m.team1Code)}–{teamLabel(m.team2Flag, m.team2Code)}
      </td>
      <td className="py-2 text-center font-extrabold">{score(m.myScoreT1, m.myScoreT2)}</td>
      <td className="py-2 text-center font-extrabold text-[var(--color-accent-red)]">
        {score(m.rivalScoreT1, m.rivalScoreT2)}
      </td>
      <td className="py-2 text-center text-[var(--color-text-muted)]">
        {score(m.actualScoreT1, m.actualScoreT2)}
      </td>
    </tr>
  );
}

function ModeButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick}
      className={`border-[1.5px] px-3 py-1.5 font-mono text-[11px] font-bold uppercase tracking-[0.12em] ${active ? "border-[var(--color-line-ink)] bg-[var(--color-accent-gold)] text-[var(--color-text-primary)]" : "border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"}`}>
      {children}
    </button>
  );
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick}
      className={`-mb-[1.5px] border-b-[3px] px-3 py-2 font-mono text-[11px] font-bold uppercase tracking-[0.12em] ${active ? "border-[var(--color-accent-red)] text-[var(--color-text-primary)]" : "border-transparent text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"}`}>
      {children}
    </button>
  );
}

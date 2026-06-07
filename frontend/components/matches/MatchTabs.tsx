"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import type { MatchesView, MatchView } from "@/lib/api/matches";
import { formatMatchDateTime } from "@/lib/format-datetime";
import { groupMatchesByStage } from "@/lib/matches-by-stage";
import { MatchListItem } from "./MatchListItem";

type Tab = "past" | "today" | "upcoming";
type Mode = "date" | "stage";

export function MatchTabs({ view, timeZone }: { view: MatchesView; timeZone: string }) {
  const t = useTranslations("matches");
  // Reuse the phase-rail round labels so the match list and the rail never drift
  // (both show e.g. "16vos" for R32, never the raw code).
  const tRound = useTranslations("home");

  const [mode, setMode] = useState<Mode>("date");

  const initial: Tab =
    view.today.length > 0 ? "today" : view.upcoming.length > 0 ? "upcoming" : "past";
  const [tab, setTab] = useState<Tab>(initial);

  // Authoritative "now" from the backend so SSR + hydration agree on live/started.
  const now = new Date(view.serverTime).getTime();

  // Per-match labels, shared by both views.
  const labelsFor = (m: MatchView) => ({
    yourPick: t("yourPick"),
    result: t("result"),
    noPick: t("noPick"),
    live: t("liveDot"),
    formatPoints: (n: number) => t("pointsEarned", { n }),
    kickoff: formatMatchDateTime(m.kickoffAt, timeZone),
    groupLabel: m.groupCode ? t("groupLabel", { code: m.groupCode }) : null,
    roundLabel: tRound(`chip${m.roundCode}` as never),
  });

  const list = tab === "past" ? view.past : tab === "today" ? view.today : view.upcoming;
  const emptyLabel =
    tab === "past" ? t("emptyPast") : tab === "today" ? t("emptyToday") : t("emptyUpcoming");

  return (
    <div className="flex flex-col">
      {/* View toggle: date tabs vs stage sections */}
      <div className="mb-3 flex gap-1.5">
        <ModeButton active={mode === "date"} onClick={() => setMode("date")}>
          {t("viewByDate")}
        </ModeButton>
        <ModeButton active={mode === "stage"} onClick={() => setMode("stage")}>
          {t("viewByStage")}
        </ModeButton>
      </div>

      {mode === "date" ? (
        <>
          <div className="flex gap-1 border-b-[1.5px] border-[var(--color-line-ink)] px-1">
            <TabButton active={tab === "past"} onClick={() => setTab("past")}>
              {t("tabPast")} · {view.past.length}
            </TabButton>
            <TabButton active={tab === "today"} onClick={() => setTab("today")}>
              {t("tabToday")} · {view.today.length}
            </TabButton>
            <TabButton active={tab === "upcoming"} onClick={() => setTab("upcoming")}>
              {t("tabUpcoming")} · {view.upcoming.length}
            </TabButton>
          </div>

          {list.length === 0 ? (
            <div className="mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center">
              <p className="font-display text-base font-extrabold uppercase tracking-tight text-[var(--color-text-muted)]">
                {emptyLabel}
              </p>
            </div>
          ) : (
            <div className="mt-3 flex flex-col gap-2">
              {list.map((m) => (
                <MatchListItem
                  key={m.id}
                  match={m}
                  showResult={tab !== "upcoming"}
                  now={now}
                  labels={labelsFor(m)}
                />
              ))}
            </div>
          )}
        </>
      ) : (
        <div className="flex flex-col gap-5">
          {groupMatchesByStage([...view.past, ...view.today, ...view.upcoming], now).map((g) => (
            <section key={g.roundCode} className="flex flex-col gap-2">
              <div
                data-testid="stage-header"
                className="chrome-label chrome-label-muted border-b-[1.5px] border-[var(--color-line-ink)] pb-1"
              >
                {tRound(`chip${g.roundCode}` as never)}
              </div>
              {g.matches.map((m) => (
                <MatchListItem
                  key={m.id}
                  match={m}
                  showResult={new Date(m.kickoffAt).getTime() <= now}
                  now={now}
                  labels={labelsFor(m)}
                />
              ))}
            </section>
          ))}
        </div>
      )}
    </div>
  );
}

function ModeButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`border-[1.5px] px-3 py-1.5 font-mono text-[11px] font-bold uppercase tracking-[0.12em] ${
        active
          ? "border-[var(--color-line-ink)] bg-[var(--color-accent-gold)] text-[var(--color-text-primary)]"
          : "border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"
      }`}
    >
      {children}
    </button>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`-mb-[1.5px] border-b-[3px] px-3 py-2 font-mono text-[11px] font-bold uppercase tracking-[0.12em] ${
        active
          ? "border-[var(--color-accent-red)] text-[var(--color-text-primary)]"
          : "border-transparent text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"
      }`}
    >
      {children}
    </button>
  );
}

"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import type { GroupConsensusView, MatchConsensus } from "@/lib/api/compare";
import { groupMatchesByStage } from "@/lib/matches-by-stage";
import { StageSection } from "@/components/shared/StageSection";
import { ConsensusCard } from "./ConsensusCard";

type Tab = "past" | "today" | "upcoming";
type Mode = "date" | "stage";

export function GroupConsensus({ data }: { data: GroupConsensusView }) {
  const t = useTranslations("compare");
  const tRound = useTranslations("home");

  const all = [...data.past, ...data.today, ...data.upcoming];
  const [mode, setMode] = useState<Mode>("date");
  const initial: Tab = data.today.length > 0 ? "today" : data.upcoming.length > 0 ? "upcoming" : "past";
  const [tab, setTab] = useState<Tab>(initial);

  if (all.length === 0) {
    return (
      <section className="mx-auto flex w-full max-w-md flex-1 flex-col items-start justify-center gap-4 px-6 py-16">
        <h1 className="headline-display whitespace-pre-line text-[44px] sm:text-6xl">{t("lockedTitle")}</h1>
        <p className="font-sans text-base text-[var(--color-text-muted)]">{t("lockedHelp")}</p>
      </section>
    );
  }

  const list = tab === "past" ? data.past : tab === "today" ? data.today : data.upcoming;
  const emptyLabel = tab === "past" ? t("emptyPast") : tab === "today" ? t("emptyToday") : t("emptyUpcoming");

  return (
    <div className="mx-3 mt-3 flex flex-col">
      <div className="mb-3 flex gap-1.5">
        <ModeButton active={mode === "date"} onClick={() => setMode("date")}>{t("viewByDate")}</ModeButton>
        <ModeButton active={mode === "stage"} onClick={() => setMode("stage")}>{t("viewByStage")}</ModeButton>
      </div>

      {mode === "date" ? (
        <>
          <div className="flex gap-1 border-b-[1.5px] border-[var(--color-line-ink)] px-1">
            <TabButton active={tab === "past"} onClick={() => setTab("past")}>{t("tabPast")} · {data.past.length}</TabButton>
            <TabButton active={tab === "today"} onClick={() => setTab("today")}>{t("tabToday")} · {data.today.length}</TabButton>
            <TabButton active={tab === "upcoming"} onClick={() => setTab("upcoming")}>{t("tabUpcoming")} · {data.upcoming.length}</TabButton>
          </div>
          {list.length === 0 ? (
            <div className="mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center">
              <p className="font-display text-base font-extrabold uppercase tracking-tight text-[var(--color-text-muted)]">{emptyLabel}</p>
            </div>
          ) : (
            <div className="mt-3 flex flex-col gap-2">
              {list.map((m) => <ConsensusCard key={m.matchId} m={m} />)}
            </div>
          )}
        </>
      ) : (
        <div className="flex flex-col gap-2">
          {/* Compare only lists revealed matches; treat all as "started" so stages order by phase recency (later rounds first), matching the prior server component. */}
          {groupMatchesByStage(all, Number.MAX_SAFE_INTEGER).map((g, i) => (
            <StageSection key={g.roundCode} header={tRound(`chip${g.roundCode}` as never)} count={g.matches.length} defaultOpen={i === 0}>
              {g.matches.map((m) => <ConsensusCard key={m.matchId} m={m} />)}
            </StageSection>
          ))}
        </div>
      )}
    </div>
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

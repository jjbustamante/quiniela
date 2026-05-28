"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import type { MatchesView } from "@/lib/api/matches";
import { MatchListItem } from "./MatchListItem";

type Tab = "past" | "today" | "upcoming";

export function MatchTabs({ view }: { view: MatchesView }) {
  const t = useTranslations("matches");

  // Default to whichever tab has the most relevant content for the moment:
  // today first (live action), then upcoming (next match), then past (history).
  const initial: Tab =
    view.today.length > 0 ? "today" : view.upcoming.length > 0 ? "upcoming" : "past";
  const [tab, setTab] = useState<Tab>(initial);

  // Authoritative "now" from the backend so server-rendered and hydrated
  // HTML agree on the live-dot decision (Date.now() would diverge between
  // SSR and the client). Reading the response timestamp is pure.
  const now = new Date(view.serverTime).getTime();

  const list = tab === "past" ? view.past : tab === "today" ? view.today : view.upcoming;
  const emptyLabel =
    tab === "past" ? t("emptyPast") : tab === "today" ? t("emptyToday") : t("emptyUpcoming");

  return (
    <div className="flex flex-col">
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
              labels={{
                yourPick: t("yourPick"),
                result: t("result"),
                noPick: t("noPick"),
                live: t("liveDot"),
                formatPoints: (n) => t("pointsEarned", { n }),
                kickoff: formatKickoff(m.kickoffAt),
                groupLabel: m.groupCode ? t("groupLabel", { code: m.groupCode }) : null,
              }}
            />
          ))}
        </div>
      )}
    </div>
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

function formatKickoff(iso: string): string {
  try {
    const d = new Date(iso);
    const date = d
      .toLocaleDateString("es-CO", { day: "2-digit", month: "short" })
      .toUpperCase()
      .replace(".", "");
    const time = d.toLocaleTimeString("es-CO", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
    return `${date} · ${time}`;
  } catch {
    return iso;
  }
}

"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import type { MatchConsensus, MatchPick, MatchPicksView } from "@/lib/api/compare";

function teamLabel(flag: string | null, code: string | null): string {
  return `${flag ?? ""} ${code ?? "—"}`.trim();
}

function filterPicks(picks: MatchPick[], f: { score?: string; aboveOnly?: boolean }): MatchPick[] {
  return picks.filter((p) => {
    if (f.aboveOnly && !p.isAboveMe) return false;
    if (f.score && `${p.scoreT1}:${p.scoreT2}` !== f.score) return false;
    return true;
  });
}

export function ConsensusCard({ m }: { m: MatchConsensus }) {
  const t = useTranslations("compare");
  const top = m.distribution.slice(0, 4);
  const max = top.reduce((acc, s) => Math.max(acc, s.count), 1);
  const mineKey = m.myScoreT1 !== null ? `${m.myScoreT1}:${m.myScoreT2}` : null;

  const [picks, setPicks] = useState<MatchPicksView | null>(null);
  const [filter, setFilter] = useState<{ score?: string; aboveOnly?: boolean } | null>(null);

  async function open(score?: string, aboveOnly?: boolean) {
    setFilter({ score, aboveOnly });
    if (!picks) {
      const { fetchMatchPicks } = await import("@/lib/actions/compare-picks");
      const data = await fetchMatchPicks(m.matchId);
      setPicks(data);
    }
  }

  return (
    <div className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-3">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-extrabold">
          {teamLabel(m.team1Flag, m.team1Code)}–{teamLabel(m.team2Flag, m.team2Code)}
        </span>
        {m.rebel ? (
          <span className="rounded-full bg-[var(--color-accent-gold)] px-2 py-0.5 text-[10px] font-extrabold uppercase text-[var(--color-line-ink)]">{t("rebel")}</span>
        ) : m.majority ? (
          <span className="rounded-full bg-[var(--color-line-ink)] px-2 py-0.5 text-[10px] font-extrabold uppercase text-[var(--color-bg-paper)]">{t("majority")}</span>
        ) : null}
      </div>
      {m.rivalsAboveTotal > 0 && (
        <p className="mb-2 text-[10px] font-bold uppercase tracking-wide text-[var(--color-text-muted)]">
          {t("rivalsAbove", { n: m.rivalsAboveTotal })}
        </p>
      )}
      {top.map((s) => {
        const key = `${s.scoreT1}:${s.scoreT2}`;
        const isMine = key === mineKey;
        return (
          <div key={key} className="mb-1 flex items-center gap-2 text-xs">
            <button
              type="button"
              className={`w-9 font-extrabold text-left ${isMine ? "text-[var(--color-accent-red)]" : ""}`}
              onClick={() => open(key)}
            >
              {s.scoreT1}–{s.scoreT2}
            </button>
            <span className="h-3.5 flex-1 overflow-hidden rounded bg-[var(--color-line-soft,#e7ddcc)]">
              <span className={`block h-full ${isMine ? "bg-[var(--color-accent-red)]" : "bg-[#cbb8a0]"}`} style={{ width: `${Math.round((s.count / max) * 100)}%` }} />
            </span>
            <span className="w-10 text-right font-bold text-[var(--color-text-muted)]">{isMine ? t("youTag") : s.count}</span>
            {s.rivalsAboveCount > 0 && (
              <button
                type="button"
                data-testid="rivals-above-mark"
                className="ml-1 shrink-0 rounded bg-[var(--color-accent-gold)] px-1 text-[9px] font-extrabold text-[var(--color-line-ink)]"
                onClick={() => open(undefined, true)}
              >
                ↑{s.rivalsAboveCount}
              </button>
            )}
          </div>
        );
      })}
      {filter && (
        <div className="mt-3 border-t border-[var(--color-line-soft,#e7ddcc)] pt-2">
          <div className="mb-1 flex items-center justify-between">
            <span className="text-[10px] font-extrabold uppercase text-[var(--color-text-muted)]">
              {filter.aboveOnly ? t("picksAboveOnly") : t("picksTitle")}
            </span>
            <button type="button" className="text-[10px] font-bold uppercase text-[var(--color-accent-red)]" onClick={() => setFilter(null)}>
              {t("picksClose")}
            </button>
          </div>
          {picks === null ? null : (
            <ul className="flex flex-col gap-0.5">
              {filterPicks(picks.picks, filter).map((p, i) => (
                <li key={`${p.rank}-${i}`} className={`flex items-center justify-between text-xs ${p.isYou ? "font-extrabold text-[var(--color-accent-red)]" : ""}`}>
                  <span>{p.isAboveMe ? "↑ " : ""}<span>{p.displayName ?? "—"}</span>{p.isBot ? " 🤖" : ""}</span>
                  <span className="font-bold">
                    {p.scoreT1}–{p.scoreT2}
                    {p.pointsEarned !== null ? ` · +${p.pointsEarned}` : ""}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

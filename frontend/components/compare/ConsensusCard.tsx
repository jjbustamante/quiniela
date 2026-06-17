"use client";

import { useTranslations } from "next-intl";
import type { MatchConsensus } from "@/lib/api/compare";

function teamLabel(flag: string | null, code: string | null): string {
  return `${flag ?? ""} ${code ?? "—"}`.trim();
}

export function ConsensusCard({ m }: { m: MatchConsensus }) {
  const t = useTranslations("compare");
  const top = m.distribution.slice(0, 4);
  const max = top.reduce((acc, s) => Math.max(acc, s.count), 1);
  const mineKey = m.myScoreT1 !== null ? `${m.myScoreT1}:${m.myScoreT2}` : null;

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
            <span className={`w-9 font-extrabold ${isMine ? "text-[var(--color-accent-red)]" : ""}`}>{s.scoreT1}–{s.scoreT2}</span>
            <span className="h-3.5 flex-1 overflow-hidden rounded bg-[var(--color-line-soft,#e7ddcc)]">
              <span className={`block h-full ${isMine ? "bg-[var(--color-accent-red)]" : "bg-[#cbb8a0]"}`} style={{ width: `${Math.round((s.count / max) * 100)}%` }} />
            </span>
            <span className="w-10 text-right font-bold text-[var(--color-text-muted)]">{isMine ? t("youTag") : s.count}</span>
            {s.rivalsAboveCount > 0 && (
              <span data-testid="rivals-above-mark"
                className="ml-1 shrink-0 rounded bg-[var(--color-accent-gold)] px-1 text-[9px] font-extrabold text-[var(--color-line-ink)]">
                ↑{s.rivalsAboveCount}
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
}

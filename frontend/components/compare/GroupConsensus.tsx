import { getTranslations } from "next-intl/server";
import type { GroupConsensusView, MatchConsensus } from "@/lib/api/compare";

function teamLabel(flag: string | null, code: string | null): string {
  return `${flag ?? ""} ${code ?? "—"}`.trim();
}

export async function GroupConsensus({ data }: { data: GroupConsensusView }) {
  const t = await getTranslations("compare");
  const revealed = data.matches.filter((m) => m.revealed);

  if (revealed.length === 0) {
    return (
      <section className="mx-auto flex w-full max-w-md flex-1 flex-col items-start justify-center gap-4 px-6 py-16">
        <h1 className="headline-display whitespace-pre-line text-[44px] sm:text-6xl">
          {t("lockedTitle")}
        </h1>
        <p className="font-sans text-base text-[var(--color-text-muted)]">{t("lockedHelp")}</p>
      </section>
    );
  }

  return (
    <section className="mx-3 mt-3 flex flex-col gap-3">
      {revealed.map((m) => (
        <ConsensusCard
          key={m.matchId}
          m={m}
          majorityLabel={t("majority")}
          rebelLabel={t("rebel")}
          youLabel={t("youTag")}
        />
      ))}
    </section>
  );
}

function ConsensusCard({
  m,
  majorityLabel,
  rebelLabel,
  youLabel,
}: {
  m: MatchConsensus;
  majorityLabel: string;
  rebelLabel: string;
  youLabel: string;
}) {
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
          <span className="rounded-full bg-[var(--color-accent-gold)] px-2 py-0.5 text-[10px] font-extrabold uppercase text-[var(--color-line-ink)]">
            {rebelLabel}
          </span>
        ) : m.majority ? (
          <span className="rounded-full bg-[var(--color-line-ink)] px-2 py-0.5 text-[10px] font-extrabold uppercase text-[var(--color-bg-paper)]">
            {majorityLabel}
          </span>
        ) : null}
      </div>
      {top.map((s) => {
        const key = `${s.scoreT1}:${s.scoreT2}`;
        const isMine = key === mineKey;
        return (
          <div key={key} className="mb-1 flex items-center gap-2 text-xs">
            <span
              className={`w-9 font-extrabold ${isMine ? "text-[var(--color-accent-red)]" : ""}`}
            >
              {s.scoreT1}–{s.scoreT2}
            </span>
            <span className="h-3.5 flex-1 overflow-hidden rounded bg-[var(--color-line-soft,#e7ddcc)]">
              <span
                className={`block h-full ${isMine ? "bg-[var(--color-accent-red)]" : "bg-[#cbb8a0]"}`}
                style={{ width: `${Math.round((s.count / max) * 100)}%` }}
              />
            </span>
            <span className="w-10 text-right font-bold text-[var(--color-text-muted)]">
              {isMine ? youLabel : s.count}
            </span>
          </div>
        );
      })}
    </div>
  );
}

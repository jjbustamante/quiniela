import { getTranslations } from "next-intl/server";
import type { H2HView, H2HMatch } from "@/lib/api/compare";
import { groupMatchesByStage } from "@/lib/matches-by-stage";
import { StageSection } from "@/components/shared/StageSection";

function teamLabel(flag: string | null, code: string | null): string {
  return `${flag ?? ""} ${code ?? "—"}`.trim();
}

function score(t1: number | null, t2: number | null): string {
  return t1 === null || t2 === null ? "—" : `${t1}–${t2}`;
}

export async function H2HCompare({ data }: { data: H2HView | null }) {
  const t = await getTranslations("compare");
  const tRound = await getTranslations("home");

  if (!data) {
    return (
      <section className="mx-3 mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] p-6 text-center">
        <p className="font-display text-base font-extrabold uppercase text-[var(--color-text-muted)]">
          {t("noRival")}
        </p>
      </section>
    );
  }

  const visible = data.matches.filter((m) => m.revealed);

  if (visible.length === 0) {
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
  // Compare only shows revealed (already-played / past-deadline) matches, so every
  // match counts as "started" and the ordering collapses to most-recent-stage-first
  // by kickoff. A fixed far-future bound keeps render pure (no impure Date.now()).
  const groups = groupMatchesByStage(visible, Number.MAX_SAFE_INTEGER);

  return (
    <div className="mx-3 mt-2 flex flex-col gap-2">
      <p className="px-1 py-2 text-xs font-semibold text-[var(--color-text-muted)]">
        {points
          ? t("summaryWinning", { agree: data.agreeCount, differ: data.differCount, points })
          : t("summary", { agree: data.agreeCount, differ: data.differCount })}
      </p>
      {groups.map((g, i) => {
        const differ = g.matches.filter((m) => m.state === "differ");
        const agree = g.matches.filter((m) => m.state === "agree");
        const rows = [...differ, ...agree];
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

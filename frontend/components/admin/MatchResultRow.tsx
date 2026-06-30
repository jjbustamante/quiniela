"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { AdminMatchRow } from "@/lib/api/admin";
import { formatDeadline } from "@/lib/format-datetime";
import { saveResultAction } from "@/app/admin/results/actions";

/**
 * One row of the admin results table. Bare form, autosaves on submit; shows
 * "Played" or "Modified" pill after the first save so the admin can scan
 * what's been done. Optimistic-ish — we set local state to whatever the
 * server returned, not to whatever the input said.
 */
export function MatchResultRow({ match, timeZone }: { match: AdminMatchRow; timeZone: string }) {
  const t = useTranslations("admin");
  const [scoreT1, setScoreT1] = useState<string>(
    match.scoreT1 != null ? String(match.scoreT1) : "",
  );
  const [scoreT2, setScoreT2] = useState<string>(
    match.scoreT2 != null ? String(match.scoreT2) : "",
  );
  // Snapshot played-on-mount once. We can't use the live `match.played`
  // prop because the server action calls revalidatePath, which re-runs
  // the server component and passes a now-played match in as new props
  // — that would flip "first save" rows into "Modified" by mistake.
  const [initiallyPlayed] = useState<boolean>(match.played);
  // Who advanced. Only meaningful for a knockout that ends level (penalties/ET draw);
  // for any decisive score the backend derives the winner from the score and ignores this.
  const [advancingTeamId, setAdvancingTeamId] = useState<number | null>(
    match.advancedTeamId,
  );
  const [savedThisSession, setSavedThisSession] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  const kickoff = formatDeadline(match.kickoffAt, timeZone);
  const scoresValid =
    scoreT1.trim() !== "" &&
    scoreT2.trim() !== "" &&
    /^\d{1,2}$/.test(scoreT1) &&
    /^\d{1,2}$/.test(scoreT2);
  // A knockout that ends level can't have its winner derived from the score, so the
  // admin must say who advanced. Group draws have no advancement; decisive scores derive it.
  const isKnockout = match.roundCode !== "GROUP";
  const isDraw = scoresValid && Number(scoreT1) === Number(scoreT2);
  const needsAdvancingTeam = isKnockout && isDraw;
  const canSave = scoresValid && (!needsAdvancingTeam || advancingTeamId != null);

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    startTransition(async () => {
      try {
        const result = await saveResultAction(
          match.matchId,
          Number(scoreT1),
          Number(scoreT2),
          needsAdvancingTeam ? advancingTeamId : null,
        );
        setScoreT1(String(result.scoreT1));
        setScoreT2(String(result.scoreT2));
        setSavedThisSession(true);
      } catch (err) {
        setError(err instanceof Error ? err.message : t("errorSaving"));
      }
    });
  }

  // "Modified" applies when this row was already played BEFORE this session
  // AND the admin just re-saved (i.e. replaced an existing result with a
  // correction). First-ever save of an unplayed match should read "Played".
  const wasModified = initiallyPlayed && savedThisSession;
  // Show "Played" badge whenever the row has a saved score on the server,
  // OR was just saved in this session. Excludes the "Modified" case so the
  // two badges don't both show.
  const showPlayedBadge = (match.played || savedThisSession) && !wasModified;

  return (
    <form
      onSubmit={onSubmit}
      className="grid grid-cols-[1fr_auto] items-center gap-3 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2"
    >
      <div className="min-w-0">
        <div className="flex items-center gap-2 text-sm">
          <span className="shrink-0">{match.team1?.flag ?? "🏳"}</span>
          <span className="truncate font-display font-extrabold uppercase tracking-tight">
            {match.team1?.name ?? "—"}
          </span>
          <span className="text-[var(--color-text-muted)]">vs</span>
          <span className="truncate font-display font-extrabold uppercase tracking-tight">
            {match.team2?.name ?? "—"}
          </span>
          <span className="shrink-0">{match.team2?.flag ?? "🏳"}</span>
        </div>
        <div className="mt-0.5 flex items-center gap-2">
          <span className="chrome-label chrome-label-muted">{kickoff}</span>
          {match.groupCode && (
            <span className="chrome-label chrome-label-muted">· {match.groupCode}</span>
          )}
          {showPlayedBadge && (
            <span className="chrome-label text-[var(--color-accent-green)]">
              · {t("playedBadge")}
            </span>
          )}
          {wasModified && (
            <span className="chrome-label text-[var(--color-accent-red)]">
              · {t("modifiedBadge")}
            </span>
          )}
        </div>
      </div>

      <div className="flex items-center gap-1.5">
        <input
          aria-label={`${match.team1?.code ?? "T1"} score`}
          inputMode="numeric"
          pattern="\d*"
          maxLength={2}
          value={scoreT1}
          onChange={(e) => setScoreT1(e.target.value.replace(/\D/g, ""))}
          className="w-12 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-primary)] py-1 text-center font-display text-lg font-black"
          placeholder={t("scorePlaceholderT1")}
        />
        <span className="text-[var(--color-text-muted)]">–</span>
        <input
          aria-label={`${match.team2?.code ?? "T2"} score`}
          inputMode="numeric"
          pattern="\d*"
          maxLength={2}
          value={scoreT2}
          onChange={(e) => setScoreT2(e.target.value.replace(/\D/g, ""))}
          className="w-12 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-primary)] py-1 text-center font-display text-lg font-black"
          placeholder={t("scorePlaceholderT2")}
        />
        <button
          type="submit"
          disabled={!canSave || pending}
          className="ml-1 bg-[var(--color-bg-ink)] px-3 py-1 font-display text-xs font-bold uppercase tracking-wide text-[var(--color-text-inverse)] disabled:opacity-40"
        >
          {pending ? t("saving") : t("save")}
        </button>
      </div>

      {needsAdvancingTeam && (
        <div className="col-span-2 flex flex-wrap items-center gap-2">
          <span className="chrome-label chrome-label-muted">{t("advancedLabel")}</span>
          {[match.team1, match.team2].map((team) =>
            team == null ? null : (
              <button
                key={team.id}
                type="button"
                aria-label={`${team.name ?? team.code ?? team.id} advanced`}
                aria-pressed={advancingTeamId === team.id}
                onClick={() => setAdvancingTeamId(team.id)}
                className={`flex items-center gap-1 border-[1.5px] border-[var(--color-line-ink)] px-2 py-0.5 font-display text-xs font-bold uppercase tracking-wide ${
                  advancingTeamId === team.id
                    ? "bg-[var(--color-bg-ink)] text-[var(--color-text-inverse)]"
                    : "bg-[var(--color-bg-paper)]"
                }`}
              >
                <span>{team.flag ?? "🏳"}</span>
                <span className="truncate">{team.name ?? team.code ?? "—"}</span>
              </button>
            ),
          )}
        </div>
      )}

      {error && (
        <div className="col-span-2 text-xs text-[var(--color-accent-red)]">{error}</div>
      )}
    </form>
  );
}


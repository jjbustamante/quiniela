"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";

const PRESETS = ["1-0", "2-1", "0-0", "1-1", "2-0", "0-1"] as const;

export type MatchContext = {
  team1Id?: number | null;
  team1Name: string | null;
  team1Flag: string | null;
  team2Id?: number | null;
  team2Name: string | null;
  team2Flag: string | null;
};

type CommonProps = {
  onCancel: () => void;
  match?: MatchContext;
  /** When true, a draw triggers a "who advances?" step to pick the winner. */
  knockout?: boolean;
};

type Props =
  | (CommonProps & { side: "t1" | "t2"; onConfirm: (n: number) => void })
  | (CommonProps & { side: "both"; onConfirm: (s: { t1: number; t2: number; predictedWinnerId?: number | null }) => void });

/**
 * Score numpad — bottom sheet over a dimmed backdrop. Two big numeral
 * cells (selected = green poster, unselected = dashed-border placeholder),
 * preset row, 3-column keypad. Escape closes; clicking the scrim closes;
 * Confirm button is the red poster CTA.
 *
 * In knockout mode, entering a draw score triggers a second step where the
 * player picks which team advances (via extra time / penalties).
 */
export function NumpadScoreInput(props: Props) {
  const t = useTranslations("numpad");
  const locale = useLocale();

  const [val, setVal] = useState<number | null>(null);
  const [t1Val, setT1Val] = useState<number | null>(null);
  const [t2Val, setT2Val] = useState<number | null>(null);
  const [active, setActive] = useState<"t1" | "t2">("t1");
  // knockout draw step: waiting for winner pick
  const [pickingWinner, setPickingWinner] = useState(false);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") props.onCancel();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [props]);

  const isKnockout = props.knockout === true;

  function pickPreset(preset: string) {
    const [a, b] = preset.split("-").map(Number);
    if (props.side === "both") {
      if (isKnockout && a === b) {
        setT1Val(a);
        setT2Val(b);
        setPickingWinner(true);
      } else {
        props.onConfirm({ t1: a, t2: b });
      }
    } else if (props.side === "t1") props.onConfirm(a);
    else props.onConfirm(b);
  }

  function tapDigit(n: number) {
    if (props.side === "both") {
      if (active === "t1") {
        setT1Val(n);
        setActive("t2");
      } else {
        setT2Val(n);
      }
    } else {
      setVal(n);
    }
  }

  function confirm() {
    if (props.side === "both") {
      if (t1Val == null || t2Val == null) return;
      if (isKnockout && t1Val === t2Val) {
        setPickingWinner(true);
        return;
      }
      props.onConfirm({ t1: t1Val, t2: t2Val });
    } else {
      if (val == null) return;
      props.onConfirm(val);
    }
  }

  function pickWinner(teamId: number | null | undefined) {
    if (props.side === "both" && t1Val != null && t2Val != null) {
      props.onConfirm({ t1: t1Val, t2: t2Val, predictedWinnerId: teamId });
    }
  }

  const canConfirm =
    props.side === "both" ? t1Val != null && t2Val != null : val != null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={t("title")}
      lang={locale}
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/70 backdrop-blur-sm sm:items-center"
      onClick={props.onCancel}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md border-t-2 border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4 pb-6 sm:border-2"
      >
        {/* Match header (poster) */}
        {props.match ? (
          <div className="mb-3.5 flex items-center justify-between gap-2 bg-[var(--color-bg-ink)] px-4 py-3 text-[var(--color-text-inverse)]">
            <div className="flex items-center gap-2.5">
              <span className="text-[28px] leading-none">{props.match.team1Flag}</span>
              <span className="font-display text-lg font-black uppercase tracking-tight">
                {props.match.team1Name}
              </span>
            </div>
            <span className="font-display text-xs font-bold text-[var(--color-accent-gold)]">VS</span>
            <div className="flex items-center gap-2.5">
              <span className="font-display text-lg font-black uppercase tracking-tight">
                {props.match.team2Name}
              </span>
              <span className="text-[28px] leading-none">{props.match.team2Flag}</span>
            </div>
          </div>
        ) : (
          <h2 className="chrome-label mb-3.5 text-[var(--color-accent-red)]">{t("title")}</h2>
        )}

        {/* Knockout draw: winner pick step */}
        {pickingWinner && props.side === "both" && (
          <>
            <div className="mb-3 bg-[var(--color-bg-ink)] px-3 py-2 text-center text-[var(--color-text-inverse)]">
              <span className="chrome-label text-[var(--color-accent-gold)]">{t("whoAdvances")}</span>
              <div className="chrome-label mt-0.5 text-white/70">
                {t1Val}–{t2Val} · {t("afterRegulation")}
              </div>
            </div>
            <div className="mb-3 grid grid-cols-2 gap-2">
              {[
                { id: props.match?.team1Id, name: props.match?.team1Name, flag: props.match?.team1Flag },
                { id: props.match?.team2Id, name: props.match?.team2Name, flag: props.match?.team2Flag },
              ].map((team) => (
                <button
                  key={team.id ?? team.name}
                  type="button"
                  onClick={() => pickWinner(team.id)}
                  className="flex flex-col items-center border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] py-4 hover:bg-[var(--color-accent-gold)]"
                >
                  <span className="text-[32px] leading-none">{team.flag}</span>
                  <span className="chrome-label mt-1.5 truncate text-[var(--color-text-primary)]">
                    {team.name}
                  </span>
                </button>
              ))}
            </div>
            <button
              type="button"
              onClick={() => setPickingWinner(false)}
              className="w-full border-[1.5px] border-[var(--color-line-ink)] py-2 font-mono text-[11px] font-bold uppercase tracking-[0.1em] text-[var(--color-text-muted)]"
            >
              ← {t("back")}
            </button>
          </>
        )}

        {/* Big numerals */}
        {!pickingWinner && props.side === "both" ? (
          <div
            className="mb-3 grid grid-cols-2 gap-2"
            aria-live="polite"
          >
            <button
              type="button"
              onClick={() => setActive("t1")}
              className={`flex flex-col items-center border-[1.5px] border-[var(--color-line-ink)] py-3 ${
                active === "t1"
                  ? "bg-[var(--color-accent-green)] text-[var(--color-text-inverse)]"
                  : "bg-[var(--color-bg-paper)] border-dashed text-[var(--color-text-muted)]"
              }`}
            >
              <span className="chrome-label" style={active === "t1" ? { color: "rgba(255,255,255,0.7)" } : undefined}>
                {props.match?.team1Name ?? "1"}
              </span>
              <span className="font-display text-[56px] font-black leading-none tracking-[-0.05em]">
                {t1Val ?? "_"}
              </span>
            </button>
            <button
              type="button"
              onClick={() => setActive("t2")}
              className={`flex flex-col items-center border-[1.5px] border-[var(--color-line-ink)] py-3 ${
                active === "t2"
                  ? "bg-[var(--color-accent-green)] text-[var(--color-text-inverse)]"
                  : "bg-[var(--color-bg-paper)] border-dashed text-[var(--color-text-muted)]"
              }`}
            >
              <span className="chrome-label" style={active === "t2" ? { color: "rgba(255,255,255,0.7)" } : undefined}>
                {props.match?.team2Name ?? "2"}
              </span>
              <span className="font-display text-[56px] font-black leading-none tracking-[-0.05em]">
                {t2Val ?? "_"}
              </span>
            </button>
          </div>
        ) : (
          <div className="mb-3 flex justify-center">
            <span className="font-display text-[64px] font-black leading-none tracking-[-0.05em] text-[var(--color-accent-red)]">
              {val ?? "_"}
            </span>
          </div>
        )}

        {!pickingWinner && <span className="chrome-label chrome-label-muted">{t("presets")}</span>}
        {!pickingWinner && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {PRESETS.map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => pickPreset(p)}
                className="border-[1.5px] border-[var(--color-line-ink)] px-3 py-1 font-mono text-xs font-bold tracking-wider text-[var(--color-text-primary)] hover:bg-[var(--color-bg-primary)]"
              >
                {p}
              </button>
            ))}
          </div>
        )}

        {!pickingWinner && <div className="mt-3 grid grid-cols-3 gap-1.5">
          {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => (
            <button
              key={n}
              type="button"
              onClick={() => tapDigit(n)}
              className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-primary)] py-3 font-display text-2xl font-black leading-none tracking-[-0.04em] text-[var(--color-text-primary)]"
            >
              {n}
            </button>
          ))}
          <button
            type="button"
            onClick={props.onCancel}
            className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] py-3 font-mono text-[11px] font-bold uppercase tracking-[0.1em] text-[var(--color-text-muted)]"
          >
            {t("cancel")}
          </button>
          <button
            type="button"
            onClick={() => tapDigit(0)}
            className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-primary)] py-3 font-display text-2xl font-black leading-none tracking-[-0.04em] text-[var(--color-text-primary)]"
          >
            0
          </button>
          <button
            type="button"
            onClick={confirm}
            disabled={!canConfirm}
            className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-accent-red)] py-3 font-mono text-[11px] font-bold uppercase tracking-[0.1em] text-[var(--color-text-inverse)] disabled:opacity-50"
          >
            {t("confirm")}
          </button>
        </div>}
      </div>
    </div>
  );
}

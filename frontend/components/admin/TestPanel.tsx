"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { TestState } from "@/lib/api/test-mode";

export function TestPanel({
  state,
  setModeAction,
  cleanAction,
  deadlinesAction,
  simulateRoundAction,
  simulateAllAction,
}: {
  state: TestState;
  setModeAction: (enabled: boolean) => Promise<void>;
  cleanAction: () => Promise<void>;
  deadlinesAction: (gs: string | null, ko: string | null) => Promise<void>;
  simulateRoundAction: () => Promise<void>;
  simulateAllAction: () => Promise<void>;
}) {
  const t = useTranslations("test");
  const [isPending, startTransition] = useTransition();
  const [gs, setGs] = useState(toLocalInput(state.groupStageDeadline));
  const [ko, setKo] = useState(toLocalInput(state.knockoutDeadline));

  const run = (fn: () => Promise<void>) => startTransition(() => void fn());

  const section =
    "border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4 flex flex-col gap-3";
  const btn =
    "self-start bg-[var(--color-bg-ink)] px-4 py-2.5 font-display text-sm font-bold uppercase tracking-wide text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-red)] disabled:opacity-50";

  if (!state.testMode) {
    return (
      <div className={section}>
        <span className="chrome-label chrome-label-muted">{t("modeOff")}</span>
        <button
          type="button"
          className={btn}
          disabled={isPending}
          onClick={() => run(() => setModeAction(true))}
        >
          {t("enable")}
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className={section}>
        <span className="chrome-label text-[var(--color-accent-green)]">{t("modeOn")}</span>
        <button
          type="button"
          className={btn}
          disabled={isPending}
          onClick={() => {
            if (confirm(t("confirmGoLive"))) run(() => setModeAction(false));
          }}
        >
          {t("disable")}
        </button>
      </div>

      <div className={section}>
        <span className="chrome-label chrome-label-muted">{t("deadlinesTitle")}</span>
        <label className="text-xs text-[var(--color-text-muted)]">
          {t("groupDeadline")}
          <input
            type="datetime-local"
            value={gs}
            onChange={(e) => setGs(e.target.value)}
            className="mt-1 block w-full border-[1.5px] border-[var(--color-line-ink)] bg-white px-2 py-1.5"
          />
        </label>
        <label className="text-xs text-[var(--color-text-muted)]">
          {t("knockoutDeadline")}
          <input
            type="datetime-local"
            value={ko}
            onChange={(e) => setKo(e.target.value)}
            className="mt-1 block w-full border-[1.5px] border-[var(--color-line-ink)] bg-white px-2 py-1.5"
          />
        </label>
        <p className="text-xs text-[var(--color-text-muted)]">{t("deadlinesHelp")}</p>
        <button
          type="button"
          className={btn}
          disabled={isPending}
          onClick={() => run(() => deadlinesAction(fromLocalInput(gs), fromLocalInput(ko)))}
        >
          {t("save")}
        </button>
      </div>

      <div className={section}>
        <span className="chrome-label chrome-label-muted">
          {state.currentRoundCode
            ? t("currentRound", { round: state.currentRoundCode })
            : t("allPlayed")}
        </span>
        <button
          type="button"
          className={btn}
          disabled={isPending || !state.currentRoundCode}
          onClick={() => run(simulateRoundAction)}
        >
          {t("simulateRound")}
        </button>
        <button
          type="button"
          className={btn}
          disabled={isPending || !state.currentRoundCode}
          onClick={() => run(simulateAllAction)}
        >
          {t("simulateAll")}
        </button>
      </div>

      <div className={section}>
        <span className="chrome-label text-[var(--color-accent-red)]">{t("cleanTitle")}</span>
        <button
          type="button"
          className={btn}
          disabled={isPending}
          onClick={() => {
            if (confirm(t("confirmClean"))) run(cleanAction);
          }}
        >
          {t("clean")}
        </button>
      </div>
    </div>
  );
}

function toLocalInput(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fromLocalInput(v: string): string | null {
  if (!v) return null;
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return null;
  return d.toISOString();
}

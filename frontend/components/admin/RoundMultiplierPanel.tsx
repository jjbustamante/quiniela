"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { RoundMultiplierRow } from "@/lib/api/round-multipliers";
import type { SaveResult } from "@/app/admin/config/actions";

type Props = {
  rounds: RoundMultiplierRow[];
  saveAction: (input: {
    rounds: { code: string; multiplier: number }[];
  }) => Promise<SaveResult>;
};

const fieldClass =
  "w-20 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2 text-center font-display text-base font-extrabold text-[var(--color-text-primary)]";
const sectionClass =
  "flex flex-col gap-3 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4";

export function RoundMultiplierPanel({ rounds, saveAction }: Props) {
  const t = useTranslations("roundMultipliers");

  const [values, setValues] = useState<number[]>(rounds.map((r) => r.multiplier));
  const [isPending, startTransition] = useTransition();
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const valid = values.every((v) => Number.isInteger(v) && v >= 1 && v <= 10);

  function setValue(i: number, v: number) {
    setSaved(false);
    setValues((prev) => prev.map((p, idx) => (idx === i ? v : p)));
  }

  function save() {
    setError(null);
    startTransition(async () => {
      const res = await saveAction({
        rounds: rounds.map((r, i) => ({ code: r.code, multiplier: values[i] })),
      });
      if (res.ok) setSaved(true);
      else setError(res.error);
    });
  }

  return (
    <section className={sectionClass}>
      <div className="chrome-label chrome-label-muted">{t("title")}</div>
      <p className="text-xs text-[var(--color-text-muted)]">{t("intro")}</p>
      <p className="text-xs font-semibold text-[var(--color-accent-red)]">{t("lockWarning")}</p>

      <ul className="flex flex-col gap-2">
        {rounds.map((r, i) => (
          <li key={r.code} className="flex items-center justify-between gap-3">
            <label
              htmlFor={`mult-${r.code}`}
              className="font-sans text-sm text-[var(--color-text-primary)]"
            >
              {r.name}
            </label>
            <input
              id={`mult-${r.code}`}
              type="number"
              min={1}
              max={10}
              step={1}
              value={Number.isFinite(values[i]) ? values[i] : ""}
              onChange={(e) => setValue(i, Math.trunc(Number(e.target.value)))}
              className={fieldClass}
            />
          </li>
        ))}
      </ul>

      {!valid && <p className="text-xs text-[var(--color-accent-red)]">{t("rangeError")}</p>}
      {error && <p className="text-xs text-[var(--color-accent-red)]">{error}</p>}

      <button
        type="button"
        onClick={save}
        disabled={!valid || isPending}
        className="bg-[var(--color-accent-red)] px-4 py-3 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-inverse)] disabled:opacity-50 hover:bg-[var(--color-bg-ink)]"
      >
        {saved ? t("saved") : t("save")}
      </button>
    </section>
  );
}

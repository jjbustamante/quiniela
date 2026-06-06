"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { PoolConfig } from "@/lib/api/pool-config";
import type { SaveResult } from "@/app/admin/config/actions";

type Props = {
  config: PoolConfig;
  saveAction: (input: {
    entryFeeCents: number;
    houseCutPercentage: number;
    prizeSplit: { rank: number; percentage: number }[];
  }) => Promise<SaveResult>;
};

const fieldClass =
  "w-full border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5 font-display text-base font-extrabold text-[var(--color-text-primary)]";
const labelClass = "chrome-label chrome-label-muted";
const sectionClass =
  "flex flex-col gap-2 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4";

export function PoolConfigPanel({ config, saveAction }: Props) {
  const t = useTranslations("moneyConfig");

  // Entry fee is edited in whole currency units (e.g. dollars), stored as cents.
  const [feeAmount, setFeeAmount] = useState((config.entryFeeCents / 100).toString());
  const [houseCut, setHouseCut] = useState(config.houseCutPercentage.toString());
  const [prizes, setPrizes] = useState<number[]>(config.prizeSplit.map((p) => p.percentage));

  const [isPending, startTransition] = useTransition();
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const feeCents = Math.round(Number(feeAmount) * 100);
  const houseCutNum = Number(houseCut);
  const prizeSum = prizes.reduce((a, b) => a + (Number.isFinite(b) ? b : 0), 0);

  const valid =
    Number.isFinite(feeCents) &&
    feeCents >= 0 &&
    Number.isInteger(houseCutNum) &&
    houseCutNum >= 0 &&
    houseCutNum <= 100 &&
    prizes.length > 0 &&
    prizes.every((p) => Number.isInteger(p) && p > 0 && p <= 100) &&
    prizeSum === 100;

  function setPrize(i: number, value: number) {
    setSaved(false);
    setPrizes((prev) => prev.map((p, idx) => (idx === i ? value : p)));
  }
  function addRank() {
    setSaved(false);
    setPrizes((prev) => [...prev, 0]);
  }
  function removeRank(i: number) {
    setSaved(false);
    setPrizes((prev) => prev.filter((_, idx) => idx !== i));
  }

  function save() {
    setSaved(false);
    setError(null);
    startTransition(async () => {
      const result = await saveAction({
        entryFeeCents: feeCents,
        houseCutPercentage: houseCutNum,
        prizeSplit: prizes.map((percentage, idx) => ({ rank: idx + 1, percentage })),
      });
      if (result.ok) setSaved(true);
      else setError(result.error);
    });
  }

  return (
    <div className="flex flex-col gap-3">
      {/* Entry fee */}
      <div className={sectionClass}>
        <label className={labelClass} htmlFor="fee">
          {t("entryFee", { currency: config.currency })}
        </label>
        <input
          id="fee"
          type="number"
          min={0}
          step="0.01"
          inputMode="decimal"
          value={feeAmount}
          onChange={(e) => {
            setSaved(false);
            setFeeAmount(e.target.value);
          }}
          className={fieldClass}
        />
      </div>

      {/* House cut */}
      <div className={sectionClass}>
        <label className={labelClass} htmlFor="housecut">
          {t("houseCut")}
        </label>
        <input
          id="housecut"
          type="number"
          min={0}
          max={100}
          step="1"
          inputMode="numeric"
          value={houseCut}
          onChange={(e) => {
            setSaved(false);
            setHouseCut(e.target.value);
          }}
          className={fieldClass}
        />
        <p className="text-xs text-[var(--color-text-muted)]">{t("houseCutHelp")}</p>
      </div>

      {/* Prize split */}
      <div className={sectionClass}>
        <span className={labelClass}>{t("prizeSplit")}</span>
        {prizes.map((p, i) => (
          <div key={i} className="flex items-center gap-2">
            <span className="w-20 shrink-0 font-display text-sm font-extrabold uppercase text-[var(--color-text-primary)]">
              {t("place", { n: i + 1 })}
            </span>
            <input
              type="number"
              min={1}
              max={100}
              step="1"
              inputMode="numeric"
              value={p}
              onChange={(e) => setPrize(i, Number(e.target.value))}
              className={fieldClass}
              aria-label={t("place", { n: i + 1 })}
            />
            <span className="font-display text-base font-extrabold text-[var(--color-text-muted)]">
              %
            </span>
            {prizes.length > 1 && (
              <button
                type="button"
                onClick={() => removeRank(i)}
                aria-label={t("removeRank")}
                className="shrink-0 px-2 py-1 font-display text-lg font-black leading-none text-[var(--color-accent-red)]"
              >
                ×
              </button>
            )}
          </div>
        ))}
        <button
          type="button"
          onClick={addRank}
          className="self-start font-display text-xs font-bold uppercase tracking-wide text-[var(--color-text-muted)] underline"
        >
          {t("addRank")}
        </button>
        <div className="flex items-center justify-between border-t-[1.5px] border-dashed border-[var(--color-line-ink)] pt-2">
          <span className={labelClass}>{t("total")}</span>
          <span
            className={`font-display text-base font-black ${
              prizeSum === 100
                ? "text-[var(--color-accent-green)]"
                : "text-[var(--color-accent-red)]"
            }`}
          >
            {prizeSum}%
          </span>
        </div>
        {prizeSum !== 100 && (
          <p className="text-xs text-[var(--color-accent-red)]">{t("mustSum100")}</p>
        )}
      </div>

      <button
        type="button"
        onClick={save}
        disabled={!valid || isPending}
        className="bg-[var(--color-bg-ink)] py-3 font-display text-sm font-bold uppercase tracking-wide text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-red)] disabled:opacity-50"
      >
        {t("save")}
      </button>
      {saved && <span className="chrome-label text-[var(--color-accent-green)]">{t("saved")}</span>}
      {error && <span className="chrome-label text-[var(--color-accent-red)]">{error}</span>}
    </div>
  );
}

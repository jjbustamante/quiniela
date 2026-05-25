"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";

const PRESETS = ["1-0", "2-1", "0-0", "1-1", "2-0", "0-1"] as const;

type Props =
  | { side: "t1" | "t2"; onConfirm: (n: number) => void; onCancel: () => void }
  | { side: "both"; onConfirm: (s: { t1: number; t2: number }) => void; onCancel: () => void };

export function NumpadScoreInput(props: Props) {
  const t = useTranslations("numpad");
  const locale = useLocale();
  const [val, setVal] = useState<number | null>(null);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") props.onCancel();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [props]);

  function pickPreset(preset: string) {
    const [a, b] = preset.split("-").map(Number);
    if (props.side === "both") props.onConfirm({ t1: a, t2: b });
    else if (props.side === "t1") props.onConfirm(a);
    else props.onConfirm(b);
  }

  function confirmDigit() {
    if (val == null) return;
    if (props.side === "both") props.onConfirm({ t1: val, t2: 0 });
    else props.onConfirm(val);
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={t("title")}
      lang={locale}
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/80 backdrop-blur-sm sm:items-center"
      onClick={props.onCancel}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-t-2xl border-t-2 border-[var(--color-border-accent)] bg-[#111c2e] p-5 shadow-2xl shadow-[var(--color-accent-cyan)]/20 sm:rounded-2xl sm:border-2 sm:border-t-2"
      >
        <h2 className="chrome-label text-[var(--color-accent-cyan)]">{t("title")}</h2>

        <div className="mt-4">
          <div className="chrome-label">{t("presets")}</div>
          <div className="mt-1 flex flex-wrap gap-2">
            {PRESETS.map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => pickPreset(p)}
                className="rounded-md border border-[var(--color-border-subtle)] px-3 py-1 font-mono-num text-sm text-[var(--color-text-primary)] hover:border-[var(--color-accent-cyan)]"
              >
                {p}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-4">
          <div
            aria-live="polite"
            className="mb-2 text-center font-mono-num text-3xl font-bold text-[var(--color-accent-cyan)]"
          >
            {val ?? "_"}
          </div>
          <div className="grid grid-cols-3 gap-2">
            {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => setVal(n)}
                className="rounded-md bg-[var(--color-bg-primary)] py-3 font-mono-num text-lg text-[var(--color-text-primary)] hover:bg-[var(--color-bg-elevated)]"
              >
                {n}
              </button>
            ))}
            <button
              type="button"
              onClick={props.onCancel}
              className="rounded-md bg-[var(--color-bg-primary)] py-3 text-sm text-[var(--color-text-muted)] hover:bg-[var(--color-bg-elevated)]"
            >
              {t("cancel")}
            </button>
            <button
              type="button"
              onClick={() => setVal(0)}
              className="rounded-md bg-[var(--color-bg-primary)] py-3 font-mono-num text-lg text-[var(--color-text-primary)] hover:bg-[var(--color-bg-elevated)]"
            >
              0
            </button>
            <button
              type="button"
              onClick={confirmDigit}
              disabled={val == null}
              className="rounded-md bg-[var(--color-accent-cyan)] py-3 text-sm font-bold text-black disabled:opacity-50"
            >
              {t("confirm")}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

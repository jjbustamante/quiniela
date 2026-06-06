"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import { PaulBadge } from "@/components/PaulMascot";
import { paulFillAllAction, type PaulFillResult } from "@/app/home/actions";
import { fillAllFeedback, pickKey, FILL_NOTHING_KEYS } from "@/lib/paul-feedback";

/**
 * Hero CTA — red poster, Paul badge + uppercase display label. Used in the
 * lobby's action row. After running, surfaces a result line under the button:
 * how many picks Paul filled, a funny line when there was nothing to fill, the
 * locked notice (backend 423), or a generic error.
 */
export function PaulFillAllButton() {
  const t = useTranslations("lobby");
  const [pending, start] = useTransition();
  const [result, setResult] = useState<{ value: PaulFillResult; nothingKey: string } | null>(null);

  function resultLine() {
    if (!result) return null;
    const fb = fillAllFeedback(result.value);
    switch (fb.kind) {
      case "filled":
        return t("fillDone", { count: fb.count });
      case "nothing":
        return t(result.nothingKey);
      case "locked":
        return t("fillLocked");
      case "error":
        return t("fillError");
    }
  }

  const line = resultLine();
  const isError = result != null && !result.value.ok;

  return (
    <div className="flex w-full flex-col gap-1.5">
      <button
        type="button"
        onClick={() =>
          start(async () => {
            const value = await paulFillAllAction();
            setResult({ value, nothingKey: pickKey(FILL_NOTHING_KEYS, Math.random()) });
          })
        }
        disabled={pending}
        className="flex w-full items-center justify-center gap-2.5 bg-[var(--color-accent-red)] px-4 py-3.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-inverse)] disabled:opacity-50 hover:bg-[var(--color-bg-ink)]"
      >
        <PaulBadge size={22} />
        {t("askPaulFillAll")}
      </button>
      {line && (
        <span
          className={`chrome-label text-center ${
            isError ? "text-[var(--color-accent-red)]" : "text-[var(--color-text-primary)]"
          }`}
        >
          {line}
        </span>
      )}
    </div>
  );
}

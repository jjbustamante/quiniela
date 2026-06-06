"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import { PaulBadge } from "@/components/PaulMascot";
import { paulFillAllAction, type PaulFillResult } from "@/app/home/actions";

/**
 * Hero CTA — red poster, Paul badge + uppercase display label. Used in the
 * lobby's action row. Surfaces a locked-round notice when betting is closed
 * (the backend returns 423 and the action reports it back).
 */
export function PaulFillAllButton() {
  const t = useTranslations("lobby");
  const [pending, start] = useTransition();
  const [result, setResult] = useState<PaulFillResult | null>(null);

  return (
    <div className="flex w-full flex-col gap-1.5">
      <button
        type="button"
        onClick={() =>
          start(async () => {
            setResult(await paulFillAllAction());
          })
        }
        disabled={pending}
        className="flex w-full items-center justify-center gap-2.5 bg-[var(--color-accent-red)] px-4 py-3.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-inverse)] disabled:opacity-50 hover:bg-[var(--color-bg-ink)]"
      >
        <PaulBadge size={22} />
        {t("askPaulFillAll")}
      </button>
      {result && !result.ok && result.locked && (
        <span className="chrome-label text-center text-[var(--color-accent-red)]">
          {t("fillLocked")}
        </span>
      )}
    </div>
  );
}

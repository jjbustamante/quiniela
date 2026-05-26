"use client";

import { useTransition } from "react";
import { useTranslations } from "next-intl";
import { PaulBadge } from "@/components/PaulMascot";
import { paulFillAllAction } from "@/app/home/actions";

/**
 * Hero CTA — red poster, Paul badge + uppercase display label. Used in the
 * lobby's action row.
 */
export function PaulFillAllButton() {
  const t = useTranslations("lobby");
  const [pending, start] = useTransition();
  return (
    <button
      type="button"
      onClick={() => start(() => paulFillAllAction())}
      disabled={pending}
      className="flex w-full items-center justify-center gap-2.5 bg-[var(--color-accent-red)] px-4 py-3.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-inverse)] disabled:opacity-50 hover:bg-[var(--color-bg-ink)]"
    >
      <PaulBadge size={22} />
      {t("askPaulFillAll")}
    </button>
  );
}

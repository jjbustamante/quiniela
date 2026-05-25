"use client";

import { useTransition } from "react";
import { useTranslations } from "next-intl";
import { paulFillAllAction } from "@/app/home/actions";

export function PaulFillAllButton() {
  const t = useTranslations("lobby");
  const [pending, start] = useTransition();
  return (
    <button
      type="button"
      onClick={() => start(() => paulFillAllAction())}
      disabled={pending}
      className="w-full rounded-md border border-[var(--color-accent-purple)] bg-[var(--color-accent-purple)]/10 py-3 chrome-label text-[var(--color-accent-purple)] hover:bg-[var(--color-accent-purple)]/20 disabled:opacity-50"
    >
      {t("askPaulFillAll")}
    </button>
  );
}

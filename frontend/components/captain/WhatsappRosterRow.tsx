"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { RosterEntry } from "@/lib/api/captain-whatsapp";
import type { ToggleResult } from "@/app/captain/whatsapp/actions";

export function WhatsappRosterRow({
  entry,
  setVisibilityAction,
}: {
  entry: RosterEntry;
  setVisibilityAction: (input: { userId: number; visible: boolean }) => Promise<ToggleResult>;
}) {
  const t = useTranslations("captainWhatsapp");
  const [visible, setVisible] = useState(entry.visible);
  const [isPending, startTransition] = useTransition();

  function handleToggle() {
    const next = !visible;
    startTransition(async () => {
      const result = await setVisibilityAction({ userId: entry.userId, visible: next });
      if (result.ok) setVisible(next);
    });
  }

  return (
    <div className="flex items-stretch border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)]">
      {/* Name column */}
      <div className="flex min-w-0 flex-1 items-center px-3 py-2">
        <span className="truncate font-display text-base font-extrabold uppercase tracking-tight">
          {entry.displayName}
        </span>
      </div>

      {/* Toggle button */}
      <div className="flex shrink-0 items-center border-l-[1.5px] border-[var(--color-line-ink)] px-3 py-2">
        <button
          type="button"
          onClick={handleToggle}
          disabled={isPending}
          aria-pressed={visible}
          className={`px-2.5 py-1 font-mono text-xs font-bold tracking-[0.08em] transition-opacity disabled:opacity-50 ${
            visible
              ? "bg-[var(--color-accent-green)] text-[var(--color-text-primary)]"
              : "bg-[var(--color-bg-ink)] text-[var(--color-text-inverse)]"
          }`}
        >
          {visible ? t("visibleOn") : t("visibleOff")}
        </button>
      </div>
    </div>
  );
}

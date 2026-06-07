"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { CommunityConfig } from "@/lib/api/community-config";
import type { SaveResult } from "@/app/admin/config/actions";

type Props = {
  config: CommunityConfig;
  saveAction: (input: { url: string | null; enabled: boolean }) => Promise<SaveResult>;
};

const fieldClass =
  "w-full border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5 font-display text-base font-extrabold text-[var(--color-text-primary)]";
const labelClass = "chrome-label chrome-label-muted";
const sectionClass =
  "flex flex-col gap-2 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4";

export function CommunityConfigPanel({ config, saveAction }: Props) {
  const t = useTranslations("adminConfig");

  const [url, setUrl] = useState(config.url ?? "");
  const [enabled, setEnabled] = useState(config.enabled);

  const [isPending, startTransition] = useTransition();
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function save() {
    setSaved(false);
    setError(null);
    startTransition(async () => {
      const trimmed = url.trim();
      const result = await saveAction({
        url: trimmed === "" ? null : trimmed,
        enabled,
      });
      if (result.ok) setSaved(true);
      else setError(result.error);
    });
  }

  return (
    <div className="flex flex-col gap-3">
      {/* WhatsApp URL */}
      <div className={sectionClass}>
        <label className={labelClass} htmlFor="whatsapp-url">
          {t("whatsappUrl")}
        </label>
        <input
          id="whatsapp-url"
          type="url"
          value={url}
          onChange={(e) => {
            setSaved(false);
            setUrl(e.target.value);
          }}
          placeholder="https://chat.whatsapp.com/"
          className={fieldClass}
        />
        <p className="text-xs text-[var(--color-text-muted)]">{t("whatsappUrlHelp")}</p>
      </div>

      {/* Enabled toggle */}
      <div className={sectionClass}>
        <label className="flex cursor-pointer items-center gap-3">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => {
              setSaved(false);
              setEnabled(e.target.checked);
            }}
            className="h-4 w-4"
          />
          <span className={labelClass}>{t("whatsappEnabled")}</span>
        </label>
      </div>

      <button
        type="button"
        onClick={save}
        disabled={isPending}
        className="bg-[var(--color-bg-ink)] py-3 font-display text-sm font-bold uppercase tracking-wide text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-red)] disabled:opacity-50"
      >
        {saved ? t("saved") : t("save")}
      </button>
      {error && <span className="chrome-label text-[var(--color-accent-red)]">{error}</span>}
    </div>
  );
}

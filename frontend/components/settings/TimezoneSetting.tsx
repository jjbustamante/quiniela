"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";

const ZONES = [
  "America/Bogota",
  "America/Caracas",
  "America/New_York",
  "America/Mexico_City",
  "America/Argentina/Buenos_Aires",
  "Europe/Madrid",
  "UTC",
];

export function TimezoneSetting({
  current,
  saveAction,
}: {
  current: string;
  saveAction: (timezone: string) => Promise<void>;
}) {
  const t = useTranslations("settings");
  const [value, setValue] = useState(current);
  const [isPending, startTransition] = useTransition();
  const [saved, setSaved] = useState(false);

  // If the user's saved zone isn't in the curated list, include it so the
  // <select> can show it as the active option.
  const options = ZONES.includes(value) ? ZONES : [value, ...ZONES];

  function save(next: string) {
    setValue(next);
    setSaved(false);
    startTransition(async () => {
      await saveAction(next);
      setSaved(true);
    });
  }

  function useDevice() {
    const detected = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (detected) save(detected);
  }

  return (
    <div className="flex flex-col gap-3">
      <label className="chrome-label chrome-label-muted" htmlFor="tz-select">
        {t("timezoneLabel")}
      </label>
      <select
        id="tz-select"
        value={value}
        disabled={isPending}
        onChange={(e) => save(e.target.value)}
        className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-primary)]"
      >
        {options.map((z) => (
          <option key={z} value={z}>
            {z}
          </option>
        ))}
      </select>
      <button
        type="button"
        onClick={useDevice}
        disabled={isPending}
        className="self-start font-mono text-[11px] font-bold uppercase tracking-[0.12em] text-[var(--color-accent-red)] disabled:opacity-50"
      >
        {t("useDevice")}
      </button>
      <p className="text-xs text-[var(--color-text-muted)]">{t("timezoneHelp")}</p>
      {saved && (
        <span className="chrome-label text-[var(--color-accent-green)]">{t("saved")}</span>
      )}
    </div>
  );
}

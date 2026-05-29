"use client";

import { useState, useSyncExternalStore, useTransition } from "react";
import { useTranslations } from "next-intl";

/**
 * IANA value → friendly Spanish label. The audience is Spanish-speaking
 * friends/family, so the dropdown shows place names instead of raw IANA ids.
 * Covers where the panas actually live; if a saved/detected zone isn't here it
 * still works (we fall back to showing the raw id).
 */
const ZONES: { value: string; label: string }[] = [
  { value: "America/Bogota", label: "Bogotá (Colombia)" },
  { value: "America/Caracas", label: "Caracas (Venezuela)" },
  { value: "America/Edmonton", label: "Calgary (Canadá)" },
  { value: "America/Sao_Paulo", label: "Curitiba / São Paulo (Brasil)" },
  { value: "America/Santiago", label: "Santiago (Chile)" },
  { value: "America/Argentina/Buenos_Aires", label: "Buenos Aires (Argentina)" },
  { value: "America/Mexico_City", label: "Ciudad de México (México)" },
  { value: "America/New_York", label: "Nueva York (EE. UU.)" },
  { value: "Europe/Madrid", label: "Madrid (España)" },
  { value: "UTC", label: "UTC" },
];

function labelFor(value: string): string {
  return ZONES.find((z) => z.value === value)?.label ?? value;
}

// The device timezone is a browser-only value. useSyncExternalStore gives us a
// null server snapshot (so SSR + first client paint agree — no hydration
// mismatch) and the real zone on the client after hydration, without a
// setState-in-effect. The store never changes, so subscribe is a no-op.
const NOOP_UNSUBSCRIBE = () => () => {};
function useDetectedZone(): string | null {
  return useSyncExternalStore(
    NOOP_UNSUBSCRIBE,
    () => Intl.DateTimeFormat().resolvedOptions().timeZone || null,
    () => null,
  );
}

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

  // Device timezone: null during SSR + first paint, the real zone after
  // hydration (see useDetectedZone). The card is a pure client-side enhancement.
  const detected = useDetectedZone();

  // If the saved zone isn't in the curated list, prepend it so the <select>
  // can show it as the active option.
  const options = ZONES.some((z) => z.value === value)
    ? ZONES
    : [{ value, label: labelFor(value) }, ...ZONES];

  function save(next: string) {
    setValue(next);
    setSaved(false);
    startTransition(async () => {
      await saveAction(next);
      setSaved(true);
    });
  }

  const detectedIsCurrent = detected !== null && detected === value;

  return (
    <div className="flex flex-col gap-4">
      {/* Auto-detect — the primary path */}
      {detected && (
        <div className="flex flex-col gap-2 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-3">
          <span className="chrome-label chrome-label-muted">{t("detectedLabel")}</span>
          <span className="font-display text-base font-extrabold uppercase tracking-tight">
            {labelFor(detected)}
          </span>
          <button
            type="button"
            onClick={() => save(detected)}
            disabled={isPending || detectedIsCurrent}
            className="self-start bg-[var(--color-bg-ink)] px-4 py-2.5 font-display text-sm font-bold uppercase tracking-wide text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-red)] disabled:opacity-50"
          >
            {detectedIsCurrent ? t("detectedActive") : t("saveDetected")}
          </button>
        </div>
      )}

      {/* Manual override */}
      <div className="flex flex-col gap-2">
        <label className="chrome-label chrome-label-muted" htmlFor="tz-select">
          {t("orChoose")}
        </label>
        <select
          id="tz-select"
          value={value}
          disabled={isPending}
          onChange={(e) => save(e.target.value)}
          className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-primary)]"
        >
          {options.map((z) => (
            <option key={z.value} value={z.value}>
              {z.label}
            </option>
          ))}
        </select>
      </div>

      <p className="text-xs text-[var(--color-text-muted)]">{t("timezoneHelp")}</p>
      {saved && (
        <span className="chrome-label text-[var(--color-accent-green)]">{t("saved")}</span>
      )}
    </div>
  );
}

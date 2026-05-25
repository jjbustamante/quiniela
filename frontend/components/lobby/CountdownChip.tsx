"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";

const KICKOFF = new Date("2026-06-11T17:00:00Z").getTime();

export function CountdownChip() {
  const t = useTranslations("lobby");
  const [days, setDays] = useState(daysUntil());
  useEffect(() => {
    const id = setInterval(() => setDays(daysUntil()), 60_000);
    return () => clearInterval(id);
  }, []);
  return (
    <span className="font-mono-num text-xs text-[var(--color-accent-cyan)] border border-[var(--color-border-accent)] rounded px-2 py-1">
      {t("countdown", { days })}
    </span>
  );
}

function daysUntil() {
  return Math.max(0, Math.ceil((KICKOFF - Date.now()) / (1000 * 60 * 60 * 24)));
}

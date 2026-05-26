"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { PosterChip } from "@/components/stats/PosterChip";
import { daysUntil } from "@/lib/tournament-format";

/**
 * Countdown poster chip — black chip with the day count in big condensed
 * type. Updates once a minute (a day-resolution counter doesn't need
 * second-resolution ticks).
 */
export function CountdownChip({ kickoffDate }: { kickoffDate: string }) {
  const t = useTranslations("lobby");
  const [days, setDays] = useState(() => daysUntil(kickoffDate));
  useEffect(() => {
    const id = setInterval(() => setDays(daysUntil(kickoffDate)), 60_000);
    return () => clearInterval(id);
  }, [kickoffDate]);
  return (
    <PosterChip
      value={days}
      label={t("countdownLabel")}
      tone="ink"
      labelClassName="text-[var(--color-accent-gold)]"
    />
  );
}

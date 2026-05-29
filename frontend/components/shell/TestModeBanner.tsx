import { getTranslations } from "next-intl/server";
import { getPublicSummaryOrFallback } from "@/lib/api/summary";

/**
 * Global "data is not real" banner. Async server component fetched once in the
 * root layout. Renders nothing unless the active tournament is in test mode
 * (FALLBACK has testMode:false, so an unreachable backend shows no banner).
 */
export async function TestModeBanner() {
  const summary = await getPublicSummaryOrFallback();
  if (!summary.testMode) return null;
  const t = await getTranslations("testMode");
  return (
    <div className="sticky top-0 z-50 bg-[var(--color-accent-red)] px-3 py-1 text-center font-mono text-[11px] font-bold uppercase tracking-[0.12em] text-[var(--color-text-inverse)]">
      {t("banner")}
    </div>
  );
}

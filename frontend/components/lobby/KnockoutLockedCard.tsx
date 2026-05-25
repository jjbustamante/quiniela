import { useTranslations } from "next-intl";

export function KnockoutLockedCard() {
  const t = useTranslations("lobby");
  return (
    <div className="flex items-center justify-between rounded-md border border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] px-4 py-3 opacity-60">
      <div>
        <div className="text-sm font-bold text-[var(--color-text-primary)]">
          🔒 {t("knockoutsHeading")}
        </div>
        <div className="mt-0.5 text-xs text-[var(--color-text-muted)]">
          {t("knockoutsLocked")}
        </div>
      </div>
    </div>
  );
}

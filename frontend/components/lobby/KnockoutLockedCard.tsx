import { useTranslations } from "next-intl";

export function KnockoutLockedCard() {
  const t = useTranslations("lobby");
  return (
    <div className="flex items-center justify-between border-l-[3px] border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] px-3 py-3 opacity-50">
      <div>
        <strong className="text-sm">🔒 {t("knockoutsHeading")}</strong>
        <div className="chrome-label">{t("knockoutsLocked")}</div>
      </div>
    </div>
  );
}

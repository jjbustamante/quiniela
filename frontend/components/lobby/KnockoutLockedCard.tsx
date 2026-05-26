import { useTranslations } from "next-intl";

/**
 * KnockoutLockedCard — dashed-border poster signaling that the bracket
 * isn't open yet. Replaces the soft rounded "locked" card with sharper
 * editorial chrome that fits the rest of the lobby.
 */
export function KnockoutLockedCard() {
  const t = useTranslations("lobby");
  return (
    <div className="border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4">
      <span className="chrome-label chrome-label-muted">🔒 {t("knockoutsHeading")}</span>
      <div className="mt-1.5 font-display text-base font-bold uppercase tracking-tight text-[var(--color-text-primary)]">
        {t("knockoutsLockedHeadline")}
      </div>
      <div className="mt-1 font-sans text-xs text-[var(--color-text-muted)]">
        {t("knockoutsLocked")}
      </div>
    </div>
  );
}

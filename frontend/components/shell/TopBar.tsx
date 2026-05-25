import { useTranslations } from "next-intl";
import { LocaleSwitcher } from "./LocaleSwitcher";

export type TopBarProps = {
  title?: string;
  meta?: React.ReactNode;
};

export function TopBar({ title, meta }: TopBarProps) {
  const t = useTranslations("common");
  return (
    <header className="flex items-center justify-between border-b-2 border-[var(--color-border-accent)] bg-[var(--color-bg-header)] px-3 py-3">
      <div className="flex items-baseline gap-3">
        <span className="chrome-label text-[var(--color-accent-cyan)]">
          {title ?? t("appName")}
        </span>
        {meta && (
          <span className="font-mono-num text-xs text-[var(--color-text-muted)]">{meta}</span>
        )}
      </div>
      <LocaleSwitcher />
    </header>
  );
}

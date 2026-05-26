import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { LocaleSwitcher } from "./LocaleSwitcher";
import { UserMenu } from "./UserMenu";

export type TopBarProps = {
  title?: string;
  meta?: React.ReactNode;
};

/**
 * Async server component. Fetches the current session and renders a UserMenu
 * (initial avatar + sign-out dropdown) when authenticated. Pages don't need
 * to thread the session through; TopBar handles it.
 */
export async function TopBar({ title, meta }: TopBarProps) {
  const t = await getTranslations("common");
  const session = await auth();
  const displayName =
    session?.user?.name ?? session?.user?.email ?? null;
  const role = session?.role;

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
      <div className="flex items-center gap-3">
        <LocaleSwitcher />
        {displayName && role && <UserMenu displayName={displayName} role={role} />}
      </div>
    </header>
  );
}

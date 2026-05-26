import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { PaulBadge } from "@/components/PaulMascot";
import { LocaleSwitcher } from "./LocaleSwitcher";
import { UserMenu } from "./UserMenu";

export type TopBarProps = {
  title?: string;
  meta?: React.ReactNode;
  /** Show the back-arrow before the mascot. */
  back?: boolean;
};

/**
 * Estadio '26 top bar — black masthead + gold mono subtitle on the left,
 * red "26" mark on the right, host-country tri-color stripe underneath.
 * Async server component; fetches the session and renders UserMenu when
 * authed. Pages don't thread session through; TopBar handles it.
 */
export async function TopBar({ title, meta, back = false }: TopBarProps) {
  const t = await getTranslations("common");
  const session = await auth();
  const displayName =
    session?.user?.name ?? session?.user?.email ?? null;
  const role = session?.role;

  return (
    <header>
      <div className="flex items-center justify-between gap-3 bg-[var(--color-bg-ink)] px-4 py-3 text-[var(--color-text-inverse)]">
        <div className="flex min-w-0 flex-1 items-center gap-2.5">
          {back && (
            <span aria-hidden="true" className="font-display text-2xl font-extrabold leading-none">←</span>
          )}
          <PaulBadge size={32} />
          <div className="min-w-0 flex-1">
            <div className="font-display text-xl font-extrabold uppercase leading-none tracking-tight truncate">
              {title ?? t("appName")}
            </div>
            {meta && (
              <div className="chrome-label mt-1 truncate text-[var(--color-accent-gold)]">
                {meta}
              </div>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <span className="font-display text-2xl font-black leading-none tracking-[-0.04em] text-[var(--color-accent-red)]">
            26
          </span>
          <LocaleSwitcher />
          {displayName && role && <UserMenu displayName={displayName} role={role} />}
        </div>
      </div>
      <div className="stripe-host"><div /><div /><div /></div>
    </header>
  );
}

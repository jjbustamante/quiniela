import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { PaulBadge } from "@/components/PaulMascot";
import { LocaleSwitcher } from "./LocaleSwitcher";
import { NavDrawer } from "./NavDrawer";

export type TopBarProps = {
  title?: string;
  meta?: React.ReactNode;
};

/**
 * Estadio '26 top bar — ☰ drawer + Paul mascot + gold mono subtitle on the
 * left, red "26" mark + locale switcher + identity initial on the right,
 * host-country tri-color stripe underneath. Async server component; fetches
 * the session and renders the (client) NavDrawer + identity mark when authed.
 */
export async function TopBar({ title, meta }: TopBarProps) {
  const t = await getTranslations("common");
  const tRoles = await getTranslations("roles");
  const session = await auth();
  const displayName = session?.user?.name ?? session?.user?.email ?? null;
  const role = session?.role;
  const initial = (displayName ?? "?").charAt(0).toUpperCase();

  return (
    <header>
      <div className="flex items-center justify-between gap-3 bg-[var(--color-bg-ink)] px-4 py-3 text-[var(--color-text-inverse)]">
        <div className="flex min-w-0 flex-1 items-center gap-2.5">
          {role && <NavDrawer role={role} />}
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
          {role && (
            <span className="chrome-label rounded-sm border border-[var(--color-accent-gold)] px-1.5 py-0.5 text-[0.625rem] leading-none text-[var(--color-accent-gold)]">
              {tRoles(role)}
            </span>
          )}
          {displayName && (
            <span
              aria-hidden="true"
              className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--color-accent-gold)] font-display text-sm font-extrabold leading-none text-[var(--color-text-primary)]"
            >
              {initial}
            </span>
          )}
        </div>
      </div>
      <div className="stripe-host"><div /><div /><div /></div>
    </header>
  );
}

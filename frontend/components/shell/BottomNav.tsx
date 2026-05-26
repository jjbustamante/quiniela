import Link from "next/link";
import { useTranslations } from "next-intl";

export type BottomNavKey = "myQuiniela" | "ranking" | "matches" | "compare";

const NAV: ReadonlyArray<{ key: BottomNavKey; href: string }> = [
  { key: "myQuiniela", href: "/home" },
  { key: "ranking", href: "/ranking" },
  { key: "matches", href: "/matches" },
  { key: "compare", href: "/compare" },
];

/**
 * Estadio '26 bottom nav — black bar, gold top-border on active tab.
 * Stays mobile-first (max-width container) so it doesn't span an
 * entire desktop window.
 */
export function BottomNav({ activeKey }: { activeKey?: BottomNavKey }) {
  const t = useTranslations("nav");
  return (
    <nav
      aria-label="primary"
      className="fixed inset-x-0 bottom-0 z-30 bg-[var(--color-bg-ink)] pb-[env(safe-area-inset-bottom)]"
    >
      <div className="mx-auto flex w-full max-w-md">
        {NAV.map(({ key, href }) => {
          const isActive = key === activeKey;
          return (
            <Link
              key={key}
              href={href}
              aria-current={isActive ? "page" : undefined}
              className={`flex flex-1 items-center justify-center border-t-[3px] py-3 font-display text-xs font-bold uppercase tracking-[0.04em] ${
                isActive
                  ? "border-[var(--color-accent-gold)] text-[var(--color-accent-gold)]"
                  : "border-transparent text-white/55 hover:text-white/80"
              }`}
            >
              {t(key)}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}

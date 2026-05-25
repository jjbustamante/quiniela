import Link from "next/link";
import { useTranslations } from "next-intl";

export type BottomNavKey = "myQuiniela" | "ranking" | "matches" | "compare";

const NAV: ReadonlyArray<{ key: BottomNavKey; href: string }> = [
  { key: "myQuiniela", href: "/home" },
  { key: "ranking", href: "/ranking" },
  { key: "matches", href: "/matches" },
  { key: "compare", href: "/compare" },
];

export function BottomNav({ activeKey }: { activeKey?: BottomNavKey }) {
  const t = useTranslations("nav");
  return (
    <nav
      aria-label="primary"
      className="fixed inset-x-0 bottom-0 flex border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-header)]"
    >
      {NAV.map(({ key, href }) => {
        const isActive = key === activeKey;
        return (
          <Link
            key={key}
            href={href}
            aria-current={isActive ? "page" : undefined}
            className={`chrome-label flex flex-1 justify-center border-t-2 py-3 ${
              isActive
                ? "border-[var(--color-accent-cyan)] font-bold text-[var(--color-accent-cyan)]"
                : "border-transparent text-[var(--color-text-muted)]"
            }`}
          >
            {t(key)}
          </Link>
        );
      })}
    </nav>
  );
}

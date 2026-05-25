"use client";

import { useLocale } from "next-intl";
import { useRouter } from "next/navigation";

export function LocaleSwitcher() {
  const router = useRouter();
  const locale = useLocale();

  function switchTo(next: string) {
    if (next === locale) return;
    // Server-side i18n/request.ts reads NEXT_LOCALE from cookies on every request.
    // router.refresh() re-runs server components with the new locale.
    document.cookie = `NEXT_LOCALE=${next}; path=/; max-age=31536000; samesite=lax`;
    router.refresh();
  }

  return (
    <div className="flex gap-1 chrome-label">
      <button
        onClick={() => switchTo("es-CO")}
        aria-pressed={locale === "es-CO"}
        className={`px-2 py-1 ${locale === "es-CO" ? "text-[var(--color-accent-cyan)]" : ""}`}
      >
        ES
      </button>
      <button
        onClick={() => switchTo("en")}
        aria-pressed={locale === "en"}
        className={`px-2 py-1 ${locale === "en" ? "text-[var(--color-accent-cyan)]" : ""}`}
      >
        EN
      </button>
    </div>
  );
}

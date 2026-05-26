"use client";

import { useLocale } from "next-intl";
import { useRouter } from "next/navigation";

/**
 * ES / EN toggle — bare mono labels, gold for active, white/55 for inactive.
 * Persists choice to NEXT_LOCALE cookie; i18n/request.ts reads it on every
 * request.
 */

// Module-scope so the cookie write isn't treated as a component-body
// side effect by react-hooks/immutability — the rule flags `document.cookie =`
// inside a component, even when it's invoked from an event handler.
function persistLocale(next: string) {
  document.cookie = `NEXT_LOCALE=${next}; path=/; max-age=31536000; samesite=lax`;
}

export function LocaleSwitcher() {
  const router = useRouter();
  const locale = useLocale();

  function switchTo(next: string) {
    if (next === locale) return;
    persistLocale(next);
    router.refresh();
  }

  return (
    <div className="flex items-center gap-0.5">
      {(["es-CO", "en"] as const).map((code) => {
        const active = locale === code;
        const label = code === "es-CO" ? "ES" : "EN";
        return (
          <button
            key={code}
            onClick={() => switchTo(code)}
            aria-pressed={active}
            className={`font-mono px-1.5 py-1 text-[10px] font-bold uppercase tracking-[0.18em] ${
              active ? "text-[var(--color-accent-gold)]" : "text-white/55 hover:text-white/80"
            }`}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
}

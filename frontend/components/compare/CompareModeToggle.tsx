"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";

export function CompareModeToggle({ mode }: { mode: "group" | "h2h" }) {
  const t = useTranslations("compare");
  const router = useRouter();
  const params = useSearchParams();

  function switchTo(next: "group" | "h2h") {
    const sp = new URLSearchParams(params.toString());
    sp.set("mode", next);
    if (next === "group") {
      sp.delete("vs");
    }
    router.push(`/compare?${sp.toString()}`);
  }

  return (
    <div className="mx-3 mt-3 flex overflow-hidden rounded-full border-[1.5px] border-[var(--color-line-ink)]">
      <button
        type="button"
        aria-pressed={mode === "h2h"}
        onClick={() => switchTo("h2h")}
        className={`flex-1 py-2 text-center text-xs font-extrabold uppercase tracking-tight ${
          mode === "h2h" ? "bg-[var(--color-line-ink)] text-[var(--color-bg-paper)]" : ""
        }`}
      >
        {t("modeH2H")}
      </button>
      <button
        type="button"
        aria-pressed={mode === "group"}
        onClick={() => switchTo("group")}
        className={`flex-1 py-2 text-center text-xs font-extrabold uppercase tracking-tight ${
          mode === "group" ? "bg-[var(--color-line-ink)] text-[var(--color-bg-paper)]" : ""
        }`}
      >
        {t("modeGroup")}
      </button>
    </div>
  );
}

"use client";

import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";

export type RivalOption = { userId: number; displayName: string | null };

export function RivalPicker({
  rivals,
  selected,
}: {
  rivals: RivalOption[];
  selected: number | null;
}) {
  const t = useTranslations("compare");
  const router = useRouter();

  return (
    <div className="mx-3 mt-3 flex items-center gap-2">
      <span className="chrome-label chrome-label-muted">{t("rivalLabel")}</span>
      <select
        id="rival-select"
        aria-label={t("pickRival")}
        className="rounded-full border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-1.5 text-sm font-bold"
        value={selected ?? ""}
        onChange={(e) => router.push(`/compare?mode=h2h&vs=${e.target.value}`)}
      >
        <option value="" disabled>
          {t("pickRival")}
        </option>
        {rivals.map((r) => (
          <option key={r.userId} value={r.userId}>
            {r.displayName ?? `#${r.userId}`}
          </option>
        ))}
      </select>
    </div>
  );
}

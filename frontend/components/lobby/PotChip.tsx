import { useTranslations } from "next-intl";

export function PotChip({ potCents, paidCount }: { potCents: number; paidCount: number }) {
  const t = useTranslations("lobby");
  const pot = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(
    potCents / 100,
  );
  return (
    <span className="font-mono-num text-xs text-[var(--color-state-good)] border border-[var(--color-border-subtle)] rounded px-2 py-1">
      {t("potChip", { pot, paid: paidCount })}
    </span>
  );
}

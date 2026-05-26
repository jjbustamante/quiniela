import { useTranslations } from "next-intl";
import { PosterChip } from "@/components/stats/PosterChip";

/**
 * Pot poster chip — gold chip with the dollar amount in display type and
 * the pana count in mono next to it.
 */
export function PotChip({ pot, panaCount }: { pot: string; panaCount: number }) {
  const t = useTranslations("lobby");
  return (
    <PosterChip
      value={pot}
      label={t("potChipPaid", { paid: panaCount })}
      tone="gold"
    />
  );
}

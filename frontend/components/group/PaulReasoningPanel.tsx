import { PaulBadge } from "@/components/PaulMascot";

/**
 * Inline panel rendered under a MatchRow after the user asks Paul. Pure
 * presentational — all copy is passed in already-localized, so it tests
 * without an i18n provider (same approach as RankingRow).
 */
export function PaulReasoningPanel({
  header,
  reasoning,
  dismissLabel,
  onDismiss,
  tone = "default",
}: {
  header: string;
  reasoning?: string;
  dismissLabel: string;
  onDismiss: () => void;
  tone?: "default" | "error";
}) {
  const hasReasoning = reasoning != null && reasoning.trim() !== "";
  return (
    <div
      className={`border-t-[1.5px] border-dashed border-[var(--color-line-ink)] px-3 py-2 ${
        tone === "error" ? "bg-[var(--color-accent-red)]/10" : "bg-[var(--color-bg-primary)]"
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <span className="inline-flex items-center gap-2 font-mono text-[10px] font-bold uppercase tracking-[0.12em] text-[var(--color-text-primary)]">
          <PaulBadge size={16} />
          {header}
        </span>
        <button
          type="button"
          onClick={onDismiss}
          className="shrink-0 font-mono text-[10px] font-bold uppercase tracking-[0.12em] text-[var(--color-text-muted)] hover:text-[var(--color-accent-red)]"
        >
          {dismissLabel}
        </button>
      </div>
      {hasReasoning && (
        <p className="mt-1.5 text-xs leading-snug text-[var(--color-text-primary)]">{reasoning}</p>
      )}
    </div>
  );
}

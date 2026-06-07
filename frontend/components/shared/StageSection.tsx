import type { ReactNode } from "react";

/**
 * Collapsible stage group. Pure presentational, zero-JS (native <details>) so it
 * works in both server and client component trees. The most-recent group passes
 * `defaultOpen` so it starts expanded while older ones stay collapsed.
 */
export function StageSection({
  header,
  count,
  defaultOpen = false,
  children,
}: {
  header: string;
  count: number;
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  return (
    <details
      open={defaultOpen}
      className="group border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)]"
    >
      <summary className="flex cursor-pointer list-none items-center justify-between gap-2 px-3 py-2 [&::-webkit-details-marker]:hidden">
        <span className="flex items-center gap-2">
          <span className="font-mono text-[10px] text-[var(--color-text-muted)] transition-transform group-open:rotate-90">
            ▸
          </span>
          <span
            data-testid="stage-header"
            className="font-display text-sm font-extrabold uppercase tracking-tight text-[var(--color-text-primary)]"
          >
            {header}
          </span>
        </span>
        <span className="font-mono text-xs font-bold text-[var(--color-text-muted)]">{count}</span>
      </summary>
      <div className="flex flex-col gap-2 px-3 pb-3">{children}</div>
    </details>
  );
}

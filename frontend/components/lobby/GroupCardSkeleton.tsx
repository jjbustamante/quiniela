export function GroupCardSkeleton({ letter }: { letter: string }) {
  return (
    <div className="flex items-center justify-between rounded-md border border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] px-4 py-3 transition-colors hover:border-[var(--color-border-accent)]/40">
      <div>
        <div className="text-sm font-bold text-[var(--color-text-primary)]">
          Grupo {letter}
        </div>
        <div className="mt-0.5 text-xs text-[var(--color-text-muted)]">
          0 / 6
        </div>
      </div>
      <div className="h-1.5 w-16 rounded-full bg-[var(--color-border-subtle)]" />
    </div>
  );
}

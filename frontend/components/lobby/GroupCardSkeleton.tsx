export function GroupCardSkeleton({ letter }: { letter: string }) {
  return (
    <div className="flex items-center justify-between border-l-[3px] border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] px-3 py-3">
      <div>
        <strong className="text-sm">Grupo {letter}</strong>
        <div className="chrome-label">0/6</div>
      </div>
      <div className="h-1 w-14 bg-[var(--color-border-subtle)]" />
    </div>
  );
}

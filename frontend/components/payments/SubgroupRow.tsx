"use client";

import { useTransition } from "react";
import type { SubgroupMember } from "@/lib/api/payments";

export function SubgroupRow({
  member,
  markPaidAction,
  paidLabel,
  unpaidLabel,
}: {
  member: SubgroupMember;
  markPaidAction: (userId: number, paid: boolean) => Promise<void>;
  paidLabel: string;
  unpaidLabel: string;
}) {
  const [isPending, startTransition] = useTransition();

  function handleToggle() {
    startTransition(async () => {
      await markPaidAction(member.userId, !member.paid);
    });
  }

  return (
    <div className="flex items-stretch border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)]">
      {/* Name column */}
      <div className="flex min-w-0 flex-1 items-center px-3 py-2">
        <span className="truncate font-display text-base font-extrabold uppercase tracking-tight">
          {member.displayName}
        </span>
      </div>

      {/* Toggle button */}
      <div className="flex shrink-0 items-center border-l-[1.5px] border-[var(--color-line-ink)] px-3 py-2">
        <button
          type="button"
          onClick={handleToggle}
          disabled={isPending}
          className={`px-2.5 py-1 font-mono text-xs font-bold tracking-[0.08em] transition-opacity disabled:opacity-50 ${
            member.paid
              ? "bg-[var(--color-accent-green)] text-[var(--color-text-primary)]"
              : "bg-[var(--color-bg-ink)] text-[var(--color-text-inverse)]"
          }`}
        >
          {member.paid ? paidLabel : unpaidLabel}
        </button>
      </div>
    </div>
  );
}

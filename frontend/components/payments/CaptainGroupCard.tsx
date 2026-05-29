"use client";

import { useTransition } from "react";
import type { CaptainGroup } from "@/lib/api/payments";
import { formatPot } from "@/lib/tournament-format";
import { PaidToggleRow } from "@/components/payments/PaidToggleRow";

export function CaptainGroupCard({
  group,
  markSettledAction,
  markPaidAction,
  settledLabel,
  notSettledLabel,
  paidLabel,
  unpaidLabel,
  collectedLabel,
}: {
  group: CaptainGroup;
  markSettledAction: (captainId: number, settled: boolean) => Promise<void>;
  markPaidAction: (userId: number, paid: boolean) => Promise<void>;
  settledLabel: string;
  notSettledLabel: string;
  paidLabel: string;
  unpaidLabel: string;
  collectedLabel: string;
}) {
  const [isSettledPending, startSettledTransition] = useTransition();

  function handleSettledToggle() {
    startSettledTransition(async () => {
      await markSettledAction(group.captainId, !group.captainSettled);
    });
  }

  return (
    <section className="mx-3 mt-5">
      {/* Captain header row */}
      <div className="flex items-stretch border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-ink)] text-[var(--color-text-inverse)]">
        {/* Captain name */}
        <div className="flex min-w-0 flex-1 items-center px-3 py-2">
          <span className="truncate font-display text-base font-extrabold uppercase tracking-tight">
            {group.captainName}
          </span>
        </div>

        {/* Collected / expected subtotal */}
        <div className="flex shrink-0 items-center border-l-[1.5px] border-[var(--color-line-ink)] px-3 py-2">
          <span className="font-mono text-xs font-bold tracking-[0.06em] text-[var(--color-text-inverse)]">
            {collectedLabel}: {formatPot(group.collectedCents, "USD")} / {formatPot(group.expectedCents, "USD")}
          </span>
        </div>

        {/* Settled toggle */}
        <div className="flex shrink-0 items-center border-l-[1.5px] border-[var(--color-line-ink)] px-3 py-2">
          <button
            type="button"
            onClick={handleSettledToggle}
            disabled={isSettledPending}
            className={`px-2.5 py-1 font-mono text-xs font-bold tracking-[0.08em] transition-opacity disabled:opacity-50 ${
              group.captainSettled
                ? "bg-[var(--color-accent-green)] text-[var(--color-text-primary)]"
                : "bg-[var(--color-bg-paper)] text-[var(--color-text-primary)]"
            }`}
          >
            {group.captainSettled ? settledLabel : notSettledLabel}
          </button>
        </div>
      </div>

      {/* Invitee rows */}
      <div className="mt-1 flex flex-col gap-1">
        {group.members.map((member) => (
          <PaidToggleRow
            key={member.userId}
            userId={member.userId}
            displayName={member.displayName}
            paid={member.paid}
            markPaidAction={markPaidAction}
            paidLabel={paidLabel}
            unpaidLabel={unpaidLabel}
          />
        ))}
      </div>
    </section>
  );
}

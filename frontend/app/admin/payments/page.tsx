import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getAdminLedger } from "@/lib/api/payments";
import { formatPot } from "@/lib/tournament-format";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { CaptainGroupCard } from "@/components/payments/CaptainGroupCard";
import { SubgroupRow } from "@/components/payments/SubgroupRow";
import { markSettledAction, markPaidAction } from "./actions";

export default async function AdminPaymentsPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const me = await getMe();
  if (me.role !== "ADMIN") redirect("/home");

  const ledger = await getAdminLedger();
  const t = await getTranslations("payments");

  const potChip = `${formatPot(ledger.potCents, "USD")} · ${ledger.paidCount}/${ledger.memberCount} ${t("potPaid")}`;

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} meta={me.displayName.toUpperCase()} />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        {/* Pot summary chip */}
        <div className="px-3 pt-3">
          <span className="chrome-label chrome-label-muted">{potChip}</span>
        </div>

        {/* Captain groups */}
        {ledger.captains.map((group) => (
          <CaptainGroupCard
            key={group.captainId}
            group={group}
            markSettledAction={markSettledAction}
            markPaidAction={markPaidAction}
            settledLabel={t("settled")}
            notSettledLabel={t("notSettled")}
            paidLabel={t("paid")}
            unpaidLabel={t("unpaid")}
            collectedLabel={t("collected")}
          />
        ))}

        {/* Orphans section */}
        {ledger.orphans.length > 0 && (
          <section className="mx-3 mt-5">
            <h2 className="font-display text-lg font-extrabold uppercase tracking-tight">
              {t("orphans")}
            </h2>
            <div className="mt-2 flex flex-col gap-1.5">
              {ledger.orphans.map((member) => (
                <SubgroupRow
                  key={member.userId}
                  member={member}
                  markPaidAction={markPaidAction}
                  paidLabel={t("paid")}
                  unpaidLabel={t("unpaid")}
                />
              ))}
            </div>
          </section>
        )}
      </div>

      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

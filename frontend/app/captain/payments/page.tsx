import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getMySubgroup } from "@/lib/api/payments";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { SubgroupRow } from "@/components/payments/SubgroupRow";
import { formatPot } from "@/lib/tournament-format";
import { markPaidAction } from "./actions";

export default async function CaptainPaymentsPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const me = await getMe();
  if (me.role === "PLAYER") redirect("/home");

  const view = await getMySubgroup();
  const t = await getTranslations("payments");
  const tNav = await getTranslations("nav");

  const collectedLabel = formatPot(view.collectedCents, "USD");
  const expectedLabel = formatPot(view.expectedCents, "USD");

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} meta={tNav("ranking")} />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        {/* Collection summary strip */}
        <div className="px-3 pt-3 flex items-center gap-4">
          <span className="chrome-label chrome-label-muted">
            {t("collected")}: {collectedLabel} / {expectedLabel}
          </span>
          <span
            className={`shrink-0 px-1.5 py-0.5 font-mono text-[9px] font-bold tracking-[0.12em] ${
              view.ownSettled
                ? "bg-[var(--color-accent-green)] text-[var(--color-text-primary)]"
                : "bg-[var(--color-bg-ink)] text-[var(--color-text-inverse)]"
            }`}
          >
            {view.ownSettled ? t("settled") : t("notSettled")}
          </span>
        </div>

        {/* Members list */}
        {view.members.length === 0 ? (
          <section className="mx-3 mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center">
            <p className="font-display text-base font-extrabold uppercase tracking-tight text-[var(--color-text-muted)]">
              {t("subgroupEmpty")}
            </p>
          </section>
        ) : (
          <section className="mx-3 mt-3 flex flex-col gap-1.5">
            {view.members.map((member) => (
              <SubgroupRow
                key={member.userId}
                member={member}
                markPaidAction={markPaidAction}
                paidLabel={t("paid")}
                unpaidLabel={t("unpaid")}
              />
            ))}
          </section>
        )}
      </div>

      <BottomNav activeKey="ranking" />
    </main>
  );
}

import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getPoolConfig } from "@/lib/api/pool-config";
import { getRoundMultipliers } from "@/lib/api/round-multipliers";
import { getCommunityConfig } from "@/lib/api/community-config";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { PoolConfigPanel } from "@/components/admin/PoolConfigPanel";
import { RoundMultiplierPanel } from "@/components/admin/RoundMultiplierPanel";
import { CommunityConfigPanel } from "@/components/admin/CommunityConfigPanel";
import { savePoolConfigAction, saveRoundMultipliersAction, saveCommunityConfigAction } from "./actions";

export default async function AdminConfigPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");
  const me = await getMe();
  if (me.role !== "ADMIN") redirect("/home");

  const config = await getPoolConfig();
  const multipliers = await getRoundMultipliers();
  const community = await getCommunityConfig();
  const t = await getTranslations("adminConfig");

  const sectionHeadClass =
    "chrome-label chrome-label-muted mb-2 font-display text-base font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-primary)]";

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} />
      <div className="mx-auto flex w-full max-w-md flex-col gap-6 px-3 pt-4 sm:max-w-2xl">
        <section>
          <h2 className={sectionHeadClass}>{t("money")}</h2>
          <PoolConfigPanel config={config} saveAction={savePoolConfigAction} />
        </section>
        <section>
          <h2 className={sectionHeadClass}>{t("points")}</h2>
          <RoundMultiplierPanel
            rounds={multipliers.rounds}
            saveAction={saveRoundMultipliersAction}
          />
        </section>
        <section>
          <h2 className={sectionHeadClass}>{t("community")}</h2>
          <CommunityConfigPanel config={community} saveAction={saveCommunityConfigAction} />
        </section>
      </div>
      <BottomNav />
    </main>
  );
}

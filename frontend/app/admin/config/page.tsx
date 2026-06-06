import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getPoolConfig } from "@/lib/api/pool-config";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { PoolConfigPanel } from "@/components/admin/PoolConfigPanel";
import { savePoolConfigAction } from "./actions";

export default async function AdminConfigPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");
  const me = await getMe();
  if (me.role !== "ADMIN") redirect("/home");

  const config = await getPoolConfig();
  const t = await getTranslations("moneyConfig");

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} />
      <div className="mx-auto w-full max-w-md px-3 pt-4 sm:max-w-2xl">
        <PoolConfigPanel config={config} saveAction={savePoolConfigAction} />
      </div>
      <BottomNav />
    </main>
  );
}

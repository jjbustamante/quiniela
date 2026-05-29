import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getTestState } from "@/lib/api/test-mode";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { TestPanel } from "@/components/admin/TestPanel";
import {
  cleanAction,
  deadlinesAction,
  setModeAction,
  simulateAllAction,
  simulateRoundAction,
} from "./actions";

export default async function AdminTestPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");
  const me = await getMe();
  if (me.role !== "ADMIN") redirect("/home");

  const state = await getTestState();
  const t = await getTranslations("test");

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl px-3 pt-4">
        <TestPanel
          state={state}
          setModeAction={setModeAction}
          cleanAction={cleanAction}
          deadlinesAction={deadlinesAction}
          simulateRoundAction={simulateRoundAction}
          simulateAllAction={simulateAllAction}
        />
      </div>
      <BottomNav />
    </main>
  );
}

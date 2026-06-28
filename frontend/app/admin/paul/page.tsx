import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getPaulJobStatus } from "@/lib/api/paul-admin";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { PaulAdminPanel } from "@/components/admin/PaulAdminPanel";
import { generateAction, revealAction, statusAction, synthesizeAction } from "./actions";

export default async function AdminPaulPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");
  const me = await getMe();
  if (me.role !== "ADMIN") redirect("/home");

  const status = await getPaulJobStatus();
  const t = await getTranslations("paulAdmin");

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl px-3 pt-4">
        <PaulAdminPanel
          initialStatus={status}
          generateAction={generateAction}
          synthesizeAction={synthesizeAction}
          revealAction={revealAction}
          statusAction={statusAction}
        />
      </div>
      <BottomNav />
    </main>
  );
}

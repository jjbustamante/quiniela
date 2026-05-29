import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { TimezoneSetting } from "@/components/settings/TimezoneSetting";
import { saveTimezoneAction } from "./actions";

export default async function SettingsPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const me = await getMe();
  const t = await getTranslations("settings");

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl px-3 pt-4">
        <section className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4">
          <TimezoneSetting current={me.timezone} saveAction={saveTimezoneAction} />
        </section>
      </div>
      <BottomNav />
    </main>
  );
}

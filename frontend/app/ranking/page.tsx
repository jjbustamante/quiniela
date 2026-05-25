import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";

export default async function RankingPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const tNav = await getTranslations("nav");
  const t = await getTranslations("placeholder");

  return (
    <main className="flex min-h-screen flex-col pb-20">
      <TopBar title={tNav("ranking")} />
      <section className="mx-auto flex w-full max-w-md flex-1 flex-col items-center justify-center gap-3 px-6 text-center sm:max-w-2xl lg:max-w-4xl">
        <h1 className="text-2xl font-semibold text-[var(--color-text-primary)]">
          {t("comingSoon")}
        </h1>
        <p className="text-[var(--color-text-muted)]">{t("rankingHelp")}</p>
      </section>
      <BottomNav activeKey="ranking" />
    </main>
  );
}

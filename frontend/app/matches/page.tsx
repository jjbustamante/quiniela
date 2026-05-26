import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";

export default async function MatchesPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const tNav = await getTranslations("nav");
  const t = await getTranslations("placeholder");

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={tNav("matches").toUpperCase()} meta={t("comingSoon").toUpperCase()} />
      <section className="mx-auto flex w-full max-w-md flex-1 flex-col items-start justify-center gap-4 px-6 sm:max-w-2xl">
        <span className="chrome-label text-[var(--color-accent-red)]">{t("comingSoon").toUpperCase()}</span>
        <h1 className="headline-display text-[44px] sm:text-6xl">
          {t("matchesHeadline")}
        </h1>
        <p className="font-sans text-base text-[var(--color-text-muted)]">{t("matchesHelp")}</p>
      </section>
      <BottomNav activeKey="matches" />
    </main>
  );
}

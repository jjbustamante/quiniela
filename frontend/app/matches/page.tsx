import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMatches } from "@/lib/api/matches";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { MatchTabs } from "@/components/matches/MatchTabs";

export default async function MatchesPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const view = await getMatches();
  const tNav = await getTranslations("nav");
  const t = await getTranslations("matches");

  const total = view.past.length + view.today.length + view.upcoming.length;

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} meta={`${total}`} />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl px-3 pt-3">
        <span className="chrome-label chrome-label-muted">{tNav("matches")}</span>
        <div className="mt-3">
          <MatchTabs view={view} />
        </div>
      </div>

      <BottomNav activeKey="matches" />
    </main>
  );
}

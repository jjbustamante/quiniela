import { Suspense } from "react";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getRanking } from "@/lib/api/ranking";
import { getGroupConsensus, getH2H } from "@/lib/api/compare";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { CompareModeToggle } from "@/components/compare/CompareModeToggle";
import { RivalPicker } from "@/components/compare/RivalPicker";
import { GroupConsensus } from "@/components/compare/GroupConsensus";
import { H2HCompare } from "@/components/compare/H2HCompare";

// Force dynamic so useSearchParams() in CompareModeToggle never triggers
// the "missing Suspense boundary" build warning.
export const dynamic = "force-dynamic";

export default async function ComparePage({
  searchParams,
}: {
  searchParams: Promise<{ mode?: string; vs?: string }>;
}) {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const sp = await searchParams;
  const mode: "group" | "h2h" = sp.mode === "h2h" ? "h2h" : "group";
  const vs = sp.vs ? Number(sp.vs) : null;

  const tNav = await getTranslations("nav");
  const ranking = await getRanking();
  const rivals = ranking.entries
    .filter((e) => !e.isYou)
    .map((e) => ({ userId: e.userId, displayName: e.displayName }));

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={tNav("compare").toUpperCase()} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <Suspense>
          <CompareModeToggle mode={mode} />
        </Suspense>
        {mode === "h2h" ? (
          <>
            <Suspense>
              <RivalPicker rivals={rivals} selected={vs} />
            </Suspense>
            <H2HCompare data={vs ? await getH2H(vs) : null} />
          </>
        ) : (
          <GroupConsensus data={await getGroupConsensus()} />
        )}
      </div>
      <BottomNav activeKey="compare" />
    </main>
  );
}

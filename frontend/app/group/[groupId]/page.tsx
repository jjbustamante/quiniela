import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMyBracket } from "@/lib/api/bracket";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { GroupDrillIn } from "@/components/group/GroupDrillIn";
import { saveBetAction, acceptPaulSuggestionAction } from "./actions";

const VALID_GROUPS = ["A","B","C","D","E","F","G","H","I","J","K","L"];

export default async function GroupPage({
  params,
}: {
  params: Promise<{ groupId: string }>;
}) {
  const { groupId } = await params;
  const upperId = groupId.toUpperCase();
  if (!VALID_GROUPS.includes(upperId)) notFound();

  const session = await auth();
  if (!session?.userId) redirect("/");

  const bracket = await getMyBracket();
  const group = bracket.groups.find((g) => g.code === upperId);
  if (!group) notFound();

  const tNav = await getTranslations("nav");

  return (
    <main className="flex min-h-screen flex-col pb-20">
      <TopBar title={`${tNav("myQuiniela")} · Grupo ${upperId}`} meta={`${group.filled}/${group.total}`} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="px-3 py-3">
          <Link
            href="/home"
            className="chrome-label inline-block text-[var(--color-accent-cyan)] hover:underline"
          >
            ← {tNav("myQuiniela")}
          </Link>
        </div>
        <div className="px-3">
          <GroupDrillIn
            matches={group.matches}
            groupId={upperId}
            saveBetAction={saveBetAction}
            acceptPaulAction={acceptPaulSuggestionAction}
          />
        </div>
      </div>
      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

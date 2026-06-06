import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getMyBracket, type MatchView } from "@/lib/api/bracket";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { GroupCard } from "@/components/lobby/GroupCard";
import { PaulFillAllButton } from "@/components/lobby/PaulFillAllButton";
import { InviteFriendsButton } from "@/components/invite/InviteFriendsButton";
import { deadlineShort } from "@/lib/tournament-format";

export default async function GroupsPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const [me, bracket] = await Promise.all([getMe(), getMyBracket()]);
  const t = await getTranslations("lobby");

  const groupLockedLabel = bracket.groupStageDeadline
    ? t("groupLockedPill", { when: deadlineShort(bracket.groupStageDeadline, me.timezone) })
    : "🔒";

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("groupsHeading")} meta={`${bracket.totalBets}/${bracket.totalMatches}`} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <section className="mx-3 mt-4">
          <div className="grid grid-cols-2 gap-2">
            {bracket.groups.map((g) => (
              <GroupCard
                key={g.code}
                letter={g.code}
                filled={g.filled}
                total={g.total}
                teams={teamPreview(g.matches)}
                locked={g.locked}
                lockedLabel={groupLockedLabel}
              />
            ))}
          </div>
        </section>
        <section className="mx-3 mt-4 flex flex-col gap-2 sm:flex-row">
          <div className="flex-1"><PaulFillAllButton /></div>
          <InviteFriendsButton role={me.role} invitePath={me.invitePath} />
        </section>
      </div>
      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

function teamPreview(matches: ReadonlyArray<MatchView>) {
  const seen = new Map<string, { code: string; flag: string | null }>();
  for (const m of matches) {
    if (m.team1Code && !seen.has(m.team1Code)) seen.set(m.team1Code, { code: m.team1Code, flag: m.team1Flag });
    if (m.team2Code && !seen.has(m.team2Code)) seen.set(m.team2Code, { code: m.team2Code, flag: m.team2Flag });
    if (seen.size === 4) break;
  }
  return Array.from(seen.values()).slice(0, 4);
}

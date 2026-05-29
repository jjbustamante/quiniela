import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMyBracket } from "@/lib/api/bracket";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { GroupDrillIn } from "@/components/group/GroupDrillIn";
import { deadlineShort } from "@/lib/tournament-format";
import { saveBetAction, acceptPaulSuggestionAction } from "./actions";

const VALID_GROUPS = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"];

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
  const tGroup = await getTranslations("group");

  // Pull the 4 distinct teams across this group's matches for the standings
  // strip — the API returns 6 round-robin matches, every team appears in 3.
  const teams = new Map<string, { code: string; name: string | null; flag: string | null }>();
  for (const m of group.matches) {
    if (m.team1Code && !teams.has(m.team1Code))
      teams.set(m.team1Code, { code: m.team1Code, name: m.team1Name, flag: m.team1Flag });
    if (m.team2Code && !teams.has(m.team2Code))
      teams.set(m.team2Code, { code: m.team2Code, name: m.team2Name, flag: m.team2Flag });
  }
  const teamList = Array.from(teams.values()).slice(0, 4);

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar
        title={`${tNav("myQuiniela").toUpperCase()} · GRUPO ${upperId}`}
        meta={`${group.filled}/${group.total}`}
      />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="px-3 pt-3">
          <Link
            href="/home"
            className="chrome-label inline-flex items-center gap-1 text-[var(--color-text-primary)] hover:text-[var(--color-accent-red)]"
          >
            ← {tNav("myQuiniela")}
          </Link>
        </div>

        {/* Standings strip — the four teams in this group as small posters */}
        <section className="mx-3 mt-3 grid grid-cols-4 gap-1.5">
          {teamList.map((t) => (
            <div
              key={t.code}
              className="border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-1.5 py-2 text-center"
            >
              <div className="text-base leading-none">{t.flag}</div>
              <div className="chrome-label chrome-label-muted mt-1">{t.code}</div>
              <div className="font-display text-lg font-extrabold leading-none tracking-[-0.04em]">·</div>
            </div>
          ))}
        </section>

        {group.locked && bracket.groupStageDeadline && (
          <section className="mx-3 mt-4 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-ink)] px-3 py-2 text-[var(--color-text-inverse)]">
            <div className="chrome-label text-[var(--color-accent-gold)]">
              {tGroup("lockedBadge", { when: deadlineShort(bracket.groupStageDeadline) })}
            </div>
            <div className="mt-0.5 text-xs text-white/70">{tGroup("lockedHelp")}</div>
          </section>
        )}

        <section className="mx-3 mt-4">
          <span className="chrome-label chrome-label-muted">{tGroup("matchesHeading", { count: group.total })}</span>
          <div className="mt-2.5">
            <GroupDrillIn
              matches={group.matches}
              groupId={upperId}
              saveBetAction={saveBetAction}
              acceptPaulAction={acceptPaulSuggestionAction}
              locked={group.locked}
            />
          </div>
        </section>
      </div>

      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

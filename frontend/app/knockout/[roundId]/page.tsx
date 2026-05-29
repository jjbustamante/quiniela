import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getMyBracket } from "@/lib/api/bracket";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { deadlineShort } from "@/lib/tournament-format";

const VALID = ["R32", "R16", "QF", "SF", "THIRD_PLACE", "FINAL"];

export default async function KnockoutPage({
  params,
}: {
  params: Promise<{ roundId: string }>;
}) {
  const { roundId } = await params;
  const upper = roundId.toUpperCase();
  if (!VALID.includes(upper)) notFound();

  const session = await auth();
  if (!session?.userId) redirect("/");

  const [me, bracket] = await Promise.all([getMe(), getMyBracket()]);
  const round = bracket.knockouts.find((k) => k.code === upper);
  if (!round) notFound();

  const tLobby = await getTranslations("lobby");
  const tNav = await getTranslations("nav");
  const tGroup = await getTranslations("group");

  return (
    <main className="flex min-h-screen flex-col pb-20">
      <TopBar title={round.name} meta={`${round.filled}/${round.total}`} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="px-3 py-3">
          <Link
            href="/home"
            className="chrome-label inline-block text-[var(--color-accent-cyan)] hover:underline"
          >
            ← {tNav("myQuiniela")}
          </Link>
        </div>
        {!round.unlocked ? (
          <div className="mx-3 mt-6 rounded-md border border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] p-6 text-center">
            <div className="text-2xl">🔒</div>
            <h1 className="mt-2 text-lg font-bold">{round.name}</h1>
            <p className="mt-1 text-sm text-[var(--color-text-muted)]">
              {tLobby("knockoutsLocked")}
            </p>
          </div>
        ) : (
          <>
            {round.locked && bracket.knockoutDeadline && (
              <div className="mx-3 mt-2 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-ink)] px-3 py-2 text-[var(--color-text-inverse)]">
                <div className="chrome-label text-[var(--color-accent-gold)]">
                  {tGroup("lockedBadge", { when: deadlineShort(bracket.knockoutDeadline, me.timezone) })}
                </div>
                <div className="mt-0.5 text-xs text-white/70">{tGroup("lockedHelp")}</div>
              </div>
            )}
            <div className="px-3 text-sm text-[var(--color-text-muted)]">
              {/* Active round UI lives here — same MatchRow pattern as the group drill-in.
                  Implemented incrementally as each round resolves in production. */}
              {round.matches.length} matches available.
            </div>
          </>
        )}
      </div>
      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

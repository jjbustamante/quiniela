import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getMyBracket } from "@/lib/api/bracket";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { KnockoutDrillIn } from "@/components/knockout/KnockoutDrillIn";
import { deadlineShort } from "@/lib/tournament-format";
import { saveBetAction, acceptPaulSuggestionAction } from "./actions";

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
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={round.name} meta={`${round.filled}/${round.total}`} />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="px-3 pt-3">
          <Link
            href="/home"
            className="chrome-label inline-flex items-center gap-1 text-[var(--color-text-primary)] hover:text-[var(--color-accent-red)]"
          >
            ← {tNav("myQuiniela")}
          </Link>
        </div>

        {!round.unlocked ? (
          <div className="mx-3 mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center">
            <div className="text-2xl">🔒</div>
            <h1 className="mt-2 font-display text-lg font-bold uppercase">{round.name}</h1>
            <p className="mt-1 text-sm text-[var(--color-text-muted)]">
              {tLobby("knockoutsLocked")}
            </p>
          </div>
        ) : (
          <>
            {round.locked && bracket.knockoutDeadline && (
              <section className="mx-3 mt-4 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-ink)] px-3 py-2 text-[var(--color-text-inverse)]">
                <div className="chrome-label text-[var(--color-accent-gold)]">
                  {tGroup("lockedBadge", { when: deadlineShort(bracket.knockoutDeadline, me.timezone) })}
                </div>
                <div className="mt-0.5 text-xs text-white/70">{tGroup("lockedHelp")}</div>
              </section>
            )}

            <section className="mx-3 mt-4">
              <span className="chrome-label chrome-label-muted">
                {tGroup("matchesHeading", { count: round.total })}
              </span>
              <div className="mt-2.5">
                <KnockoutDrillIn
                  matches={round.matches}
                  roundCode={upper}
                  saveBetAction={saveBetAction}
                  acceptPaulAction={acceptPaulSuggestionAction}
                  locked={round.locked}
                  timeZone={me.timezone}
                />
              </div>
            </section>
          </>
        )}
      </div>
      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

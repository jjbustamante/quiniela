import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getMyBracket } from "@/lib/api/bracket";
import { getRanking } from "@/lib/api/ranking";
import { getMatches } from "@/lib/api/matches";
import { getPublicSummary } from "@/lib/api/summary";
import { computeHomeState } from "@/lib/home-phase";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { FocusCard } from "@/components/lobby/FocusCard";
import { PhaseRail } from "@/components/lobby/PhaseRail";
import { StandingStrip } from "@/components/lobby/StandingStrip";
import { ResultsRecap } from "@/components/lobby/ResultsRecap";
import { InviteFriendsButton } from "@/components/invite/InviteFriendsButton";

export default async function HomePage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const [me, bracket, ranking, matches, summary] = await Promise.all([
    getMe(),
    getMyBracket(),
    getRanking(),
    getMatches(),
    getPublicSummary(),
  ]);

  const state = computeHomeState({
    bracket,
    ranking,
    matches,
    summary,
    nowMs: Date.parse(matches.serverTime),
  });

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar />
      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <FocusCard focus={state.focus} timeZone={me.timezone} />
        <PhaseRail chips={state.chips} />
        <StandingStrip standing={state.standing} />
        <ResultsRecap recap={state.recap} timeZone={me.timezone} />
        <section className="mx-3 mt-4">
          <InviteFriendsButton role={me.role} invitePath={me.invitePath} />
        </section>
      </div>
      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

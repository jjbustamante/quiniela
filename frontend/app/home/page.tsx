import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { CountdownChip } from "@/components/lobby/CountdownChip";
import { PotChip } from "@/components/lobby/PotChip";
import { GroupCardSkeleton } from "@/components/lobby/GroupCardSkeleton";
import { KnockoutLockedCard } from "@/components/lobby/KnockoutLockedCard";
import { InviteFriendsButton } from "@/components/invite/InviteFriendsButton";

const GROUP_LETTERS = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"];

export default async function HomePage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const me = await getMe();
  const t = await getTranslations("lobby");

  return (
    <main className="flex min-h-screen flex-col pb-20">
      <TopBar title={t("title")} meta={`${me.displayName} · 0/104`} />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="flex flex-wrap gap-2 px-3 py-3">
          <CountdownChip />
          <PotChip potCents={0} paidCount={0} />
        </div>

        <section className="px-3">
          <span className="chrome-label">{t("groupsHeading")}</span>
          <div className="mt-2 grid grid-cols-1 gap-1 sm:grid-cols-2 sm:gap-2 lg:grid-cols-3">
            {GROUP_LETTERS.map((letter) => (
              <GroupCardSkeleton key={letter} letter={letter} />
            ))}
          </div>
        </section>

        <section className="px-3 py-3">
          <span className="chrome-label">{t("knockoutsHeading")}</span>
          <div className="mt-2">
            <KnockoutLockedCard />
          </div>
        </section>

        <section className="px-3 py-3 space-y-2">
          <InviteFriendsButton role={me.role} invitePath={me.invitePath} />
        </section>
      </div>

      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

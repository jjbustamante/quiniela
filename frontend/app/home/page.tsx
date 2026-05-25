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

      <div className="mx-auto w-full max-w-md">
        <div className="flex flex-wrap gap-2 px-3 py-3">
          <CountdownChip />
          <PotChip potCents={0} paidCount={0} />
        </div>

        <section className="px-3 space-y-1">
          <span className="chrome-label">{t("groupsHeading")}</span>
          {GROUP_LETTERS.map((letter) => (
            <GroupCardSkeleton key={letter} letter={letter} />
          ))}
        </section>

        <section className="px-3 py-3 space-y-1">
          <span className="chrome-label">{t("knockoutsHeading")}</span>
          <KnockoutLockedCard />
        </section>

        <section className="px-3 py-3 space-y-2">
          <InviteFriendsButton role={me.role} invitePath={me.invitePath} />
        </section>
      </div>

      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

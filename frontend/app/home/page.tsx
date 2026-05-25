import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getMyBracket } from "@/lib/api/bracket";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { CountdownChip } from "@/components/lobby/CountdownChip";
import { PotChip } from "@/components/lobby/PotChip";
import { GroupCard } from "@/components/lobby/GroupCard";
import { KnockoutLockedCard } from "@/components/lobby/KnockoutLockedCard";
import { PaulFillAllButton } from "@/components/lobby/PaulFillAllButton";
import { InviteFriendsButton } from "@/components/invite/InviteFriendsButton";

export default async function HomePage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const [me, bracket] = await Promise.all([getMe(), getMyBracket()]);
  const t = await getTranslations("lobby");

  const knockoutsUnlocked = bracket.knockouts.some((k) => k.unlocked);

  return (
    <main className="flex min-h-screen flex-col pb-20">
      <TopBar title={t("title")} meta={`${me.displayName} · ${bracket.totalBets}/${bracket.totalMatches}`} />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <div className="flex flex-wrap gap-2 px-3 py-3">
          <CountdownChip />
          <PotChip potCents={0} paidCount={0} />
        </div>

        <section className="px-3">
          <span className="chrome-label">{t("groupsHeading")}</span>
          <div className="mt-2 grid grid-cols-1 gap-1 sm:grid-cols-2 sm:gap-2 lg:grid-cols-3">
            {bracket.groups.map((g) => (
              <GroupCard key={g.code} letter={g.code} filled={g.filled} total={g.total} />
            ))}
          </div>
        </section>

        <section className="px-3 py-3">
          <span className="chrome-label">{t("knockoutsHeading")}</span>
          <div className="mt-2">
            {knockoutsUnlocked ? (
              <div className="text-sm text-[var(--color-text-muted)]">
                Disponible — entra a /knockout/R32, /knockout/R16, etc.
              </div>
            ) : (
              <KnockoutLockedCard />
            )}
          </div>
        </section>

        <section className="px-3 py-3 space-y-2">
          <PaulFillAllButton />
          <InviteFriendsButton role={me.role} invitePath={me.invitePath} />
        </section>
      </div>

      <BottomNav activeKey="myQuiniela" />
    </main>
  );
}

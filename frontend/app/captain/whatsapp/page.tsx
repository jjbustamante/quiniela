import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getWhatsappRoster } from "@/lib/api/captain-whatsapp";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { WhatsappRosterRow } from "@/components/captain/WhatsappRosterRow";
import { setVisibilityAction } from "./actions";

export default async function CaptainWhatsappPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const me = await getMe();
  if (me.role === "PLAYER") redirect("/home");

  const roster = await getWhatsappRoster();
  const t = await getTranslations("captainWhatsapp");

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("title")} />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        {/* Intro text */}
        <p className="chrome-label chrome-label-muted px-3 pt-3">{t("intro")}</p>

        {/* Roster list */}
        {roster.length === 0 ? (
          <section className="mx-3 mt-6 border-[1.5px] border-dashed border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-6 text-center">
            <p className="font-display text-base font-extrabold uppercase tracking-tight text-[var(--color-text-muted)]">
              {t("empty")}
            </p>
          </section>
        ) : (
          <section className="mx-3 mt-3 flex flex-col gap-1.5">
            {roster.map((entry) => (
              <WhatsappRosterRow
                key={entry.userId}
                entry={entry}
                setVisibilityAction={setVisibilityAction}
              />
            ))}
          </section>
        )}
      </div>

      <BottomNav />
    </main>
  );
}

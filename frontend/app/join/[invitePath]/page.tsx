import { getTranslations } from "next-intl/server";
import { resolveInvite } from "@/lib/api/invite";
import { AuthButton } from "@/components/AuthButton";
import { TopBar } from "@/components/shell/TopBar";
import { rememberInvitePath } from "./actions";

type Params = { invitePath: string };

export default async function JoinPage({
  params,
}: {
  params: Promise<Params>;
}) {
  const { invitePath } = await params;
  const t = await getTranslations("invite");

  const resolution = await resolveInvite(invitePath);
  if (!resolution) {
    return (
      <main className="flex min-h-screen flex-col">
        <TopBar />
        <section className="mx-auto flex w-full max-w-md flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
          <h1 className="text-2xl font-semibold text-[var(--color-state-bad)]">
            {t("invalid")}
          </h1>
          <p className="text-[var(--color-text-muted)]">{t("invalidHelp")}</p>
        </section>
      </main>
    );
  }

  // Capture the invite path in a cookie so it's available during the
  // Auth.js OAuth round-trip.
  await rememberInvitePath(invitePath);

  return (
    <main className="flex min-h-screen flex-col">
      <TopBar />
      <section className="mx-auto flex w-full max-w-md flex-1 flex-col items-center justify-center gap-6 px-6 text-center">
        <div className="space-y-2">
          <h1 className="text-3xl font-semibold text-[var(--color-text-primary)]">
            {t("invitedBy", { name: resolution.inviterDisplayName })}
          </h1>
          <p className="text-[var(--color-text-muted)]">{t("joinPrompt")}</p>
        </div>
        <AuthButton />
      </section>
    </main>
  );
}

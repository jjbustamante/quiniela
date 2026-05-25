import { getTranslations } from "next-intl/server";
import { resolveInvite } from "@/lib/api/invite";
import { TopBar } from "@/components/shell/TopBar";
import { signInWithInvite } from "./actions";

type Params = { invitePath: string };

export default async function JoinPage({
  params,
}: {
  params: Promise<Params>;
}) {
  const { invitePath } = await params;
  const t = await getTranslations("invite");
  const tCommon = await getTranslations("common");

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
        <form action={signInWithInvite}>
          <input type="hidden" name="invitePath" value={invitePath} />
          <button
            type="submit"
            className="inline-flex items-center gap-2 rounded-md bg-[var(--color-accent-cyan)] px-6 py-3 text-sm font-bold text-black uppercase tracking-wider hover:opacity-90"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
              <path
                fill="currentColor"
                d="M21.35 11.1h-9.17v2.73h6.51c-.33 3.81-3.5 5.44-6.5 5.44C8.36 19.27 5 16.25 5 12c0-4.1 3.2-7.27 7.2-7.27 3.09 0 4.9 1.97 4.9 1.97L19 4.72S16.56 2 12.1 2C6.42 2 2.03 6.8 2.03 12c0 5.05 4.13 10 10.22 10 5.35 0 9.25-3.67 9.25-9.09 0-1.15-.15-1.81-.15-1.81z"
              />
            </svg>
            {tCommon("signIn")}
          </button>
        </form>
      </section>
    </main>
  );
}

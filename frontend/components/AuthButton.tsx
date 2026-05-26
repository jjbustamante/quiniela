import { auth, signIn, signOut } from "@/lib/auth";
import { getTranslations } from "next-intl/server";

/**
 * Server Component. Renders either a poster-style Google sign-in button
 * or a sign-out form depending on session. Server actions on submit —
 * no client JS for the auth flow.
 */
export async function AuthButton() {
  const session = await auth();
  const t = await getTranslations("common");

  if (session?.user) {
    return (
      <form
        action={async () => {
          "use server";
          await signOut({ redirectTo: "/" });
        }}
        className="flex items-center gap-3"
      >
        <div className="flex items-center gap-2 text-sm text-[var(--color-text-primary)]">
          {session.user.image && (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={session.user.image}
              alt=""
              width={28}
              height={28}
              className="rounded-full"
            />
          )}
          <span className="font-medium">{session.user.name ?? session.user.email}</span>
          {session.role === "ADMIN" && (
            <span className="chrome-label bg-[var(--color-accent-gold)] px-2 py-0.5 text-[var(--color-text-primary)]">
              ADMIN
            </span>
          )}
        </div>
        <button
          type="submit"
          className="font-mono-num text-xs font-bold uppercase tracking-[0.12em] text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"
        >
          {t("signOut")}
        </button>
      </form>
    );
  }

  return (
    <form
      action={async () => {
        "use server";
        await signIn("google", { redirectTo: "/home" });
      }}
    >
      <button
        type="submit"
        className="flex w-full items-center justify-center gap-3 bg-[var(--color-bg-ink)] px-4 py-4 font-display text-lg font-black uppercase tracking-wide text-[var(--color-text-inverse)] hover:bg-black"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
          <path fill="#fff" d="M22.5 12.3c0-.8-.1-1.5-.2-2.3H12v4.3h5.9c-.3 1.4-1 2.6-2.2 3.4v2.8h3.6c2.1-2 3.2-4.8 3.2-8.2z" />
          <path fill="#fff" d="M12 23c2.9 0 5.3-1 7.1-2.6l-3.6-2.8c-1 .7-2.3 1.1-3.5 1.1-2.7 0-5-1.8-5.8-4.3H2.4v2.8C4.2 20.6 7.9 23 12 23z" />
          <path fill="#fff" d="M6.2 14.5c-.2-.7-.3-1.4-.3-2 0-.7.1-1.4.3-2v-2.8H2.4C1.6 9.3 1 10.6 1 12s.6 2.7 1.4 4.3l3.8-1.8z" />
          <path fill="#fff" d="M12 5.5c1.5 0 2.9.5 4 1.5l3-3C17.3 2.4 14.9 1 12 1 7.9 1 4.2 3.4 2.4 7l3.8 2.8c.8-2.5 3.1-4.3 5.8-4.3z" />
        </svg>
        {t("signIn")}
      </button>
    </form>
  );
}

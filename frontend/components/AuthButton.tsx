import { auth, signIn, signOut } from "@/lib/auth";
import { getTranslations } from "next-intl/server";

/**
 * Server Component. Renders either a sign-in or sign-out form depending on
 * the current session. Uses server actions for the form submit — no client
 * JS needed for the auth flow itself.
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
        <div className="flex items-center gap-2 text-sm text-zinc-700 dark:text-zinc-300">
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
          <span>{session.user.name ?? session.user.email}</span>
          {session.role === "ADMIN" && (
            <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs font-medium text-amber-900 dark:bg-amber-900/30 dark:text-amber-200">
              admin
            </span>
          )}
        </div>
        <button
          type="submit"
          className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm font-medium text-zinc-700 hover:bg-zinc-100 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-900"
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
        className="inline-flex items-center gap-2 rounded-md bg-black px-4 py-2 text-sm font-medium text-white hover:bg-zinc-800 dark:bg-white dark:text-black dark:hover:bg-zinc-200"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
          <path
            fill="currentColor"
            d="M21.35 11.1h-9.17v2.73h6.51c-.33 3.81-3.5 5.44-6.5 5.44C8.36 19.27 5 16.25 5 12c0-4.1 3.2-7.27 7.2-7.27 3.09 0 4.9 1.97 4.9 1.97L19 4.72S16.56 2 12.1 2C6.42 2 2.03 6.8 2.03 12c0 5.05 4.13 10 10.22 10 5.35 0 9.25-3.67 9.25-9.09 0-1.15-.15-1.81-.15-1.81z"
          />
        </svg>
        {t("signIn")}
      </button>
    </form>
  );
}

import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { TopBar } from "@/components/shell/TopBar";

type SearchParams = { error?: string };

/**
 * Auth.js routes here when sign-in fails (configured via pages.error in
 * lib/auth.ts). The error code arrives as ?error=<name>. We map known names
 * thrown by our jwt callback (NoInvite, BackendError, BackendUnreachable)
 * to translated messages; everything else falls back to "generic".
 */
export default async function AuthErrorPage({
  searchParams,
}: {
  searchParams: Promise<SearchParams>;
}) {
  const { error } = await searchParams;
  const t = await getTranslations("auth_error");

  const message =
    error === "NoInvite"
      ? t("noInvite")
      : error === "BackendError"
        ? t("backendError")
        : error === "BackendUnreachable"
          ? t("backendUnreachable")
          : t("generic");

  return (
    <main className="flex min-h-screen flex-col">
      <TopBar />
      <section className="mx-auto flex w-full max-w-md flex-1 flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-2xl font-semibold text-[var(--color-state-bad)]">
          {t("title")}
        </h1>
        <p className="text-[var(--color-text-muted)]">{message}</p>
        <Link
          href="/"
          className="mt-2 rounded-md border border-[var(--color-border-accent)] px-4 py-2 text-sm font-semibold uppercase tracking-wider text-[var(--color-accent-cyan)] hover:bg-[var(--color-accent-cyan)]/10"
        >
          {t("backToHome")}
        </Link>
      </section>
    </main>
  );
}

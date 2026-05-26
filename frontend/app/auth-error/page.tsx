import Link from "next/link";
import { cookies } from "next/headers";
import { getTranslations } from "next-intl/server";
import { PaulBadge } from "@/components/PaulMascot";

type SearchParams = { error?: string };

/**
 * Auth.js routes here when sign-in fails. Estadio '26 treatment: hazard
 * stripes at the top, the big inked "ESTA ES PRIVADA." headline, a black
 * "why" explainer, and a back-to-home CTA. The specific error code drives
 * the body copy (see lib/auth.ts for the authError cookie + ?error= URL
 * fallback).
 */
export default async function AuthErrorPage({
  searchParams,
}: {
  searchParams: Promise<SearchParams>;
}) {
  const { error: urlError } = await searchParams;
  const jar = await cookies();
  const cookieError = jar.get("authError")?.value;
  const code = cookieError ?? urlError;
  const t = await getTranslations("auth_error");

  const message =
    code === "NoInvite"
      ? t("noInvite")
      : code === "BackendError"
        ? t("backendError")
        : code === "BackendUnreachable"
          ? t("backendUnreachable")
          : t("generic");

  const isInviteIssue = code === "NoInvite" || !code;

  return (
    <main className="flex min-h-screen flex-col bg-[var(--color-bg-primary)]">
      {/* Diagonal hazard stripes accent */}
      <div
        className="h-10 w-full"
        style={{
          background:
            "repeating-linear-gradient(45deg, var(--color-accent-red) 0 12px, var(--color-bg-ink) 12px 24px)",
        }}
      />

      <section className="mx-auto flex w-full max-w-md flex-1 flex-col px-5 py-6 sm:max-w-2xl sm:py-10">
        <div className="flex items-center gap-3">
          <PaulBadge size={44} />
          <div className="font-display text-xl font-black uppercase leading-[0.9] tracking-tight">
            Quiniela<br />Panas
          </div>
        </div>

        <div className="flex-1 py-10">
          <span className="chrome-label text-[var(--color-accent-red)]">{t("title").toUpperCase()}</span>

          <h1 className="headline-display mt-2 text-[48px] sm:text-6xl">
            {isInviteIssue ? (
              <>
                {t("privateHeadlinePart1")}
                <br />
                <span className="text-[var(--color-accent-red)]">{t("privateHeadlineAccent")}</span>
              </>
            ) : (
              <>{t("genericHeadline")}</>
            )}
          </h1>

          <p className="mt-4 max-w-md font-sans text-sm leading-relaxed text-[var(--color-text-muted)]">
            {message}
          </p>

          {isInviteIssue && (
            <div className="mt-5 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-ink)] p-3 text-[var(--color-text-inverse)]">
              <span className="chrome-label text-[var(--color-accent-gold)]">{t("whyTitle")}</span>
              <p className="mt-1 font-sans text-xs leading-relaxed text-white/80">
                {t("whyBody")}
              </p>
            </div>
          )}
        </div>

        <div className="flex flex-col gap-2 sm:flex-row">
          <Link
            href="/"
            className="flex-1 bg-[var(--color-bg-ink)] px-4 py-3.5 text-center font-display text-sm font-black uppercase tracking-[0.04em] text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-red)]"
          >
            {t("backToHome")}
          </Link>
        </div>
      </section>
    </main>
  );
}

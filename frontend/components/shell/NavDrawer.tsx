"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { signOutAction } from "@/app/auth-actions";

type Role = "ADMIN" | "CAPTAIN" | "PLAYER";

/**
 * NavDrawer — left slide-in menu for secondary features. The ☰ trigger lives
 * in the TopBar; the 4 principal features stay in the BottomNav. Mirrors
 * UserMenu's open/close mechanics (Escape + backdrop close). Items are
 * role-gated: everyone gets Inicio + sign-out; admin/captain get Pagos
 * (routed by role); admin also gets Resultados.
 */
export function NavDrawer({
  role,
  showWhatsappGroup = false,
}: {
  role: Role;
  showWhatsappGroup?: boolean;
}) {
  const t = useTranslations("nav");
  const tCommon = useTranslations("common");
  const [open, setOpen] = useState(false);
  const firstLinkRef = useRef<HTMLAnchorElement>(null);

  useEffect(() => {
    if (open) firstLinkRef.current?.focus();
  }, [open]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const close = () => setOpen(false);
  const paymentsHref = role === "ADMIN" ? "/admin/payments" : "/captain/payments";

  const linkClass =
    "block border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-primary)] hover:bg-[var(--color-accent-gold)]";

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={t("menu")}
        aria-expanded={open}
        aria-controls="nav-drawer-panel"
        className="flex h-8 w-8 items-center justify-center font-display text-2xl font-extrabold leading-none text-[var(--color-text-inverse)]"
      >
        ☰
      </button>
      {open && (
        <>
          <div
            className="fixed inset-0 z-40 bg-black/40"
            onClick={close}
            aria-hidden="true"
            data-testid="nav-backdrop"
          />
          <div
            id="nav-drawer-panel"
            role="dialog"
            aria-label={t("menu")}
            className="fixed inset-y-0 left-0 z-50 flex w-72 max-w-[80vw] flex-col gap-2 border-r-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4 shadow-[0_8px_28px_rgba(0,0,0,0.25)]"
          >
            <div className="chrome-label chrome-label-muted mb-2">{t("menu")}</div>
            <Link ref={firstLinkRef} href="/home" className={linkClass} onClick={close}>
              {t("home")}
            </Link>
            {(role === "ADMIN" || role === "CAPTAIN") && (
              <Link href={paymentsHref} className={linkClass} onClick={close}>
                {t("payments")}
              </Link>
            )}
            {showWhatsappGroup && (
              <Link href="/captain/whatsapp" className={linkClass} onClick={close}>
                {t("whatsappGroup")}
              </Link>
            )}
            {role === "ADMIN" && (
              <>
                <Link href="/admin/results" className={linkClass} onClick={close}>
                  {t("results")}
                </Link>
                <Link href="/admin/config" className={linkClass} onClick={close}>
                  {t("config")}
                </Link>
                <Link href="/admin/test" className={linkClass} onClick={close}>
                  {t("testMode")}
                </Link>
                <Link href="/admin/paul" className={linkClass} onClick={close}>
                  {t("paulAdmin")}
                </Link>
              </>
            )}
            <Link href="/scoring" className={linkClass} onClick={close}>
              {t("scoring")}
            </Link>
            <Link href="/settings" className={linkClass} onClick={close}>
              {t("settings")}
            </Link>
            <div className="my-1 border-t-[1.5px] border-dashed border-[var(--color-line-ink)]" />
            <form action={signOutAction}>
              <button
                type="submit"
                className="w-full bg-[var(--color-bg-ink)] py-2.5 font-display text-sm font-bold uppercase tracking-wide text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-red)]"
              >
                {tCommon("signOut")}
              </button>
            </form>
          </div>
        </>
      )}
    </>
  );
}

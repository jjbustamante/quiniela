"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { signOutAction } from "@/app/auth-actions";

type Role = "ADMIN" | "CAPTAIN" | "PLAYER";

/**
 * UserMenu — circular avatar (gold initial on ink) that opens a poster-style
 * dropdown with name, role, and sign-out. Closes on Escape and outside click.
 */
export function UserMenu({
  displayName,
  role,
}: {
  displayName: string;
  role: Role;
}) {
  const t = useTranslations("common");
  const [open, setOpen] = useState(false);
  const initial = (displayName || "?").charAt(0).toUpperCase();

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={displayName}
        aria-expanded={open}
        className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--color-accent-gold)] font-display text-sm font-extrabold leading-none text-[var(--color-text-primary)]"
      >
        {initial}
      </button>
      {open && (
        <>
          <div
            className="fixed inset-0 z-40"
            onClick={() => setOpen(false)}
            aria-hidden="true"
          />
          <div
            role="menu"
            className="absolute right-0 top-full z-50 mt-2 w-56 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4 shadow-[0_8px_28px_rgba(0,0,0,0.18)]"
          >
            <div className="mb-1 truncate font-display text-base font-extrabold uppercase tracking-tight text-[var(--color-text-primary)]">
              {displayName}
            </div>
            <div className="chrome-label chrome-label-muted mb-4">{role}</div>
            <form action={signOutAction}>
              <button
                type="submit"
                className="w-full bg-[var(--color-bg-ink)] py-2.5 font-display text-sm font-bold uppercase tracking-wide text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-red)]"
              >
                {t("signOut")}
              </button>
            </form>
          </div>
        </>
      )}
    </div>
  );
}

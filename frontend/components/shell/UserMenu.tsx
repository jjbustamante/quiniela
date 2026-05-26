"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { signOutAction } from "@/app/auth-actions";

type Role = "ADMIN" | "CAPTAIN" | "PLAYER";

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
        className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--color-accent-cyan)] text-sm font-bold text-black"
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
            className="absolute right-0 top-full z-50 mt-2 w-52 rounded-md border-2 border-[var(--color-border-accent)] bg-[#111c2e] p-3 shadow-2xl shadow-[var(--color-accent-cyan)]/20"
          >
            <div className="mb-1 truncate text-sm font-bold text-[var(--color-text-primary)]">
              {displayName}
            </div>
            <div className="mb-3 text-xs uppercase tracking-wide text-[var(--color-text-muted)]">
              {role.toLowerCase()}
            </div>
            <form action={signOutAction}>
              <button
                type="submit"
                className="w-full rounded-md border border-[var(--color-border-subtle)] py-2 text-sm font-semibold text-[var(--color-text-primary)] hover:border-[var(--color-state-bad)] hover:text-[var(--color-state-bad)]"
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

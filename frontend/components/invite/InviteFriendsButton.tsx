"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { InviteFriendsSheet } from "./InviteFriendsSheet";

type Role = "ADMIN" | "CAPTAIN" | "PLAYER";

/**
 * Estadio "+ INVITAR" — paper poster with an ink border. Hidden for plain
 * PLAYER role or when there's no invitePath available.
 */
export function InviteFriendsButton({
  role,
  invitePath,
}: {
  role: Role;
  invitePath: string | null;
}) {
  const t = useTranslations("lobby");
  const [open, setOpen] = useState(false);

  if (role === "PLAYER" || !invitePath) return null;

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="bg-[var(--color-bg-paper)] border-[1.5px] border-[var(--color-line-ink)] px-4 py-3.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-primary)] hover:bg-[var(--color-accent-gold)]"
      >
        + {t("inviteFriends")}
      </button>
      {open && <InviteFriendsSheet invitePath={invitePath} onClose={() => setOpen(false)} />}
    </>
  );
}

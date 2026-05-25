"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { InviteFriendsSheet } from "./InviteFriendsSheet";

type Role = "ADMIN" | "CAPTAIN" | "PLAYER";

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
        className="w-full rounded border border-[var(--color-border-accent)] py-3 chrome-label text-[var(--color-accent-cyan)]"
      >
        {t("inviteFriends")}
      </button>
      {open && <InviteFriendsSheet invitePath={invitePath} onClose={() => setOpen(false)} />}
    </>
  );
}

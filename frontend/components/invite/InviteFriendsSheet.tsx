"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";

export function InviteFriendsSheet({
  invitePath,
  onClose,
}: {
  invitePath: string;
  onClose: () => void;
}) {
  const t = useTranslations("invite_sheet");
  const locale = useLocale();
  const [copied, setCopied] = useState(false);

  const fullUrl =
    typeof window !== "undefined"
      ? `${window.location.origin}/join/${invitePath}`
      : `/join/${invitePath}`;

  async function copy() {
    await navigator.clipboard.writeText(fullUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  const waText = t("whatsappMessage", { url: fullUrl });
  const waHref = `https://wa.me/?text=${encodeURIComponent(waText)}`;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={t("title")}
      lang={locale}
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/80 backdrop-blur-sm sm:items-center"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-t-xl border-t-2 border-[var(--color-border-accent)] bg-[var(--color-bg-elevated)] p-5 space-y-4 shadow-2xl shadow-[var(--color-accent-cyan)]/10 sm:rounded-xl sm:border-t-0 sm:border-2"
      >
        <h2 className="chrome-label text-[var(--color-accent-cyan)]">{t("title")}</h2>
        <div>
          <span className="chrome-label">{t("linkLabel")}</span>
          <p className="break-all rounded bg-[var(--color-bg-primary)] px-3 py-2 font-mono-num text-sm">
            {fullUrl}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={copy}
            className="flex-1 rounded border border-[var(--color-border-accent)] py-3 chrome-label text-[var(--color-accent-cyan)]"
          >
            {copied ? t("copied") : t("copy")}
          </button>
          <a
            href={waHref}
            target="_blank"
            rel="noopener noreferrer"
            className="flex-1 rounded bg-[var(--color-state-good)] py-3 text-center chrome-label text-black"
          >
            {t("whatsapp")}
          </a>
        </div>
      </div>
    </div>
  );
}

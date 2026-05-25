"use client";

import { useEffect, useState } from "react";
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

  // Close on Escape — keyboard-accessible dismissal.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

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
        className="w-full max-w-md rounded-t-2xl border-t-2 border-[var(--color-border-accent)] bg-[#111c2e] p-6 shadow-2xl shadow-[var(--color-accent-cyan)]/20 sm:rounded-2xl sm:border-2 sm:border-t-2"
      >
        <div className="mb-5 flex items-start justify-between gap-4">
          <h2 className="text-lg font-bold text-[var(--color-text-primary)]">
            {t("title")}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="close"
            className="-mr-2 -mt-1 rounded-md p-1 text-[var(--color-text-muted)] hover:bg-white/5 hover:text-[var(--color-text-primary)]"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="mb-5">
          <span className="chrome-label block">{t("linkLabel")}</span>
          <p className="mt-1 break-all rounded-md border border-[var(--color-border-subtle)] bg-[var(--color-bg-primary)] px-3 py-2 font-mono text-sm text-[var(--color-text-primary)]">
            {fullUrl}
          </p>
        </div>

        <div className="flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={copy}
            className="inline-flex items-center gap-2 rounded-md border border-[var(--color-accent-cyan)] px-3 py-2 text-xs font-semibold uppercase tracking-wider text-[var(--color-accent-cyan)] hover:bg-[var(--color-accent-cyan)]/10"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
            </svg>
            {copied ? t("copied") : t("copy")}
          </button>
          <a
            href={waHref}
            target="_blank"
            rel="noopener noreferrer"
            aria-label={t("whatsapp")}
            title={t("whatsapp")}
            className="inline-flex items-center justify-center rounded-md bg-[#25d366] p-2 text-black hover:opacity-90"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 0 1-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 0 1-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893A11.821 11.821 0 0 0 20.464 3.488"/>
            </svg>
          </a>
        </div>
      </div>
    </div>
  );
}

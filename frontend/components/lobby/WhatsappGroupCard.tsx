import { useTranslations } from "next-intl";
import Link from "next/link";

/**
 * Server component — renders when the backend supplies a WhatsApp community
 * link for this viewer. Captains/admins also get a "manage who sees this" link.
 */
export function WhatsappGroupCard({
  url,
  canManage,
}: {
  url: string;
  canManage: boolean;
}) {
  const t = useTranslations("lobby");

  return (
    <div className="border-2 border-[var(--color-line-ink)] bg-[var(--color-bg-paper)]">
      <a
        href={url}
        target="_blank"
        rel="noopener noreferrer"
        className="flex items-center gap-3 px-4 py-3 hover:opacity-90"
        aria-label={t("whatsappGroupTitle")}
      >
        {/* WhatsApp glyph chip */}
        <span
          className="flex shrink-0 items-center justify-center rounded-full p-2"
          style={{ backgroundColor: "#25d366" }}
        >
          <svg
            width="22"
            height="22"
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
            className="text-[var(--color-bg-ink)]"
          >
            <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 0 1-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 0 1-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893A11.821 11.821 0 0 0 20.464 3.488" />
          </svg>
        </span>

        <div className="min-w-0 flex-1">
          <p className="font-display text-sm font-extrabold uppercase tracking-tight text-[var(--color-text-primary)]">
            {t("whatsappGroupTitle")}
          </p>
          <p className="mt-0.5 text-xs text-[var(--color-text-muted)]">
            {t("whatsappGroupSubtitle")}
          </p>
        </div>
      </a>

      {canManage && (
        <div className="border-t border-[var(--color-line-ink)] px-4 py-2">
          <Link
            href="/captain/whatsapp"
            className="text-xs text-[var(--color-accent-gold)] underline hover:opacity-80"
          >
            {t("whatsappGroupManage")}
          </Link>
        </div>
      )}
    </div>
  );
}

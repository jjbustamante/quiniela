"use client";

import { useEffect, useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { PaulJobStatus } from "@/lib/api/paul-admin";
import type {
  JobActionResult,
  RevealActionResult,
} from "@/app/admin/paul/actions";

type Props = {
  initialStatus: PaulJobStatus;
  generateAction: () => Promise<JobActionResult>;
  synthesizeAction: () => Promise<JobActionResult>;
  revealAction: () => Promise<RevealActionResult>;
  statusAction: () => Promise<PaulJobStatus>;
};

const sectionClass =
  "flex flex-col gap-3 border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4";
const primaryBtn =
  "w-full bg-[var(--color-bg-ink)] px-4 py-3 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-inverse)] disabled:opacity-50 hover:bg-[var(--color-accent-gold)] hover:text-[var(--color-text-primary)]";
const revealBtn =
  "w-full bg-[var(--color-accent-red)] px-4 py-3 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-inverse)] disabled:opacity-50 hover:bg-[var(--color-bg-ink)]";

export function PaulAdminPanel({
  initialStatus,
  generateAction,
  synthesizeAction,
  revealAction,
  statusAction,
}: Props) {
  const t = useTranslations("paulAdmin");
  const [status, setStatus] = useState<PaulJobStatus>(initialStatus);
  const [pending, startTransition] = useTransition();
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  const running = status.state === "RUNNING";
  const busy = running || pending;

  // Live-poll the job status while a job is RUNNING; stop once it settles.
  useEffect(() => {
    if (!running) return;
    const id = setInterval(async () => {
      try {
        setStatus(await statusAction());
      } catch {
        // Transient poll failure — keep the interval, try again next tick.
      }
    }, 2000);
    return () => clearInterval(id);
  }, [running, statusAction]);

  function runJob(action: () => Promise<JobActionResult>) {
    setMessage(null);
    setIsError(false);
    startTransition(async () => {
      const res = await action();
      if (res.ok) {
        setStatus(res.status);
      } else {
        setIsError(true);
        setMessage(res.conflict ? t("alreadyRunning") : t("error"));
      }
    });
  }

  function reveal() {
    setMessage(null);
    setIsError(false);
    startTransition(async () => {
      const res = await revealAction();
      if (res.ok) {
        setMessage(t("revealed", { count: res.result.betsCreated }));
      } else {
        setIsError(true);
        setMessage(res.conflict ? t("alreadyRunning") : t("error"));
      }
    });
  }

  const pct = status.total > 0 ? Math.round((status.processed / status.total) * 100) : 0;
  const showProgress = running || status.total > 0;

  return (
    <section className={sectionClass}>
      <div className="chrome-label chrome-label-muted">{t("title")}</div>
      <p className="text-xs text-[var(--color-text-muted)]">{t("intro")}</p>

      <div className="flex flex-col gap-2">
        <button
          type="button"
          onClick={() => runJob(generateAction)}
          disabled={busy}
          className={primaryBtn}
        >
          {t("generate")}
        </button>
        <button
          type="button"
          onClick={() => runJob(synthesizeAction)}
          disabled={busy}
          className={primaryBtn}
        >
          {t("synthesize")}
        </button>
        <button type="button" onClick={reveal} disabled={busy} className={revealBtn}>
          {t("reveal")}
        </button>
      </div>

      <div className="flex flex-col gap-1.5 border-t-[1.5px] border-dashed border-[var(--color-line-ink)] pt-3">
        <div className="chrome-label">
          {t("statusLabel")}: {t(`state.${status.state}`)}
          {status.phase ? ` — ${status.phase}` : ""}
        </div>

        {showProgress && (
          <div className="flex flex-col gap-1" role="status" aria-live="polite">
            <div className="h-2 w-full bg-[var(--color-bg-ink)]/10">
              <div
                className="h-full bg-[var(--color-accent-gold)] transition-all"
                style={{ width: `${pct}%` }}
              />
            </div>
            <span className="text-xs text-[var(--color-text-muted)]">
              {status.processed} / {status.total} ({pct}%)
            </span>
          </div>
        )}

        {status.state === "FAILED" && status.error && (
          <span className="text-xs text-[var(--color-accent-red)]">{status.error}</span>
        )}
      </div>

      {message && (
        <span
          className={`chrome-label text-center ${
            isError ? "text-[var(--color-accent-red)]" : "text-[var(--color-text-primary)]"
          }`}
        >
          {message}
        </span>
      )}
    </section>
  );
}

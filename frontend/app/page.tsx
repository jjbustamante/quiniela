import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { AuthButton } from "@/components/AuthButton";

type HealthResult =
  | { state: "up"; status: string }
  | { state: "down"; status: string }
  | { state: "error"; message: string };

async function fetchHealth(): Promise<HealthResult> {
  const base = process.env.API_URL ?? "http://localhost:8080";
  try {
    const res = await fetch(`${base}/actuator/health`, { cache: "no-store" });
    if (!res.ok) return { state: "down", status: `HTTP ${res.status}` };
    const data = (await res.json()) as { status?: string };
    const status = data.status ?? "UNKNOWN";
    return { state: status === "UP" ? "up" : "down", status };
  } catch (err) {
    return {
      state: "error",
      message: err instanceof Error ? err.message : String(err),
    };
  }
}

export default async function Home() {
  const session = await auth();
  if (session?.userId) redirect("/home");

  const t = await getTranslations("landing");
  const health = await fetchHealth();
  const dotColor =
    health.state === "up"
      ? "bg-[var(--color-state-good)]"
      : health.state === "down"
        ? "bg-[var(--color-state-warning)]"
        : "bg-[var(--color-state-bad)]";

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 px-6 py-24">
      <div className="flex flex-col items-center gap-3 text-center">
        <h1 className="text-5xl font-semibold tracking-tight text-[var(--color-accent-cyan)] uppercase">
          {t("title")}
        </h1>
        <p className="text-lg text-[var(--color-text-muted)]">{t("subtitle")}</p>
      </div>

      <div className="rounded-md border border-[var(--color-border-subtle)] bg-[var(--color-bg-elevated)] px-5 py-4">
        <div className="flex items-center gap-3">
          <span className={`inline-block h-2.5 w-2.5 rounded-full ${dotColor}`} />
          <span className="font-mono-num text-sm text-[var(--color-text-primary)]">
            {health.state === "error" ? t("backendDown") : `${t("backendUp")} · ${health.status}`}
          </span>
        </div>
      </div>

      <AuthButton />
    </main>
  );
}

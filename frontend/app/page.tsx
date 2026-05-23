type HealthResult =
  | { state: "up"; status: string }
  | { state: "down"; status: string }
  | { state: "error"; message: string };

async function fetchHealth(): Promise<HealthResult> {
  const base = process.env.API_URL ?? "http://localhost:8080";
  try {
    const res = await fetch(`${base}/actuator/health`, { cache: "no-store" });
    if (!res.ok) {
      return { state: "down", status: `HTTP ${res.status}` };
    }
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
  const health = await fetchHealth();
  const dotColor =
    health.state === "up"
      ? "bg-emerald-500"
      : health.state === "down"
        ? "bg-amber-500"
        : "bg-red-500";

  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-8 px-6 py-24 bg-zinc-50 dark:bg-black">
      <div className="flex flex-col items-center gap-3 text-center">
        <h1 className="text-5xl font-semibold tracking-tight text-black dark:text-zinc-50">
          Quiniela 2026
        </h1>
        <p className="text-lg text-zinc-600 dark:text-zinc-400">
          La quiniela de los panas para el Mundial.
        </p>
      </div>

      <div className="rounded-lg border border-zinc-200 bg-white px-5 py-4 dark:border-zinc-800 dark:bg-zinc-950">
        <div className="flex items-center gap-3">
          <span className={`inline-block h-2.5 w-2.5 rounded-full ${dotColor}`} />
          <span className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
            Backend:{" "}
            {health.state === "error" ? "no disponible" : health.status}
          </span>
        </div>
        {health.state === "error" && (
          <p className="mt-2 text-xs text-zinc-500 dark:text-zinc-400">
            {health.message}
          </p>
        )}
      </div>
    </main>
  );
}

import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { getMe } from "@/lib/api/me";
import { getAdminMatches, type AdminMatchRow } from "@/lib/api/admin";
import { TopBar } from "@/components/shell/TopBar";
import { BottomNav } from "@/components/shell/BottomNav";
import { MatchResultRow } from "@/components/admin/MatchResultRow";

/**
 * Admin results entry. Non-admin users are bounced to /home — the backend
 * also enforces the role check on every PUT, this redirect is just UX.
 */
export default async function AdminResultsPage() {
  const session = await auth();
  if (!session?.userId) redirect("/");

  const me = await getMe();
  if (me.role !== "ADMIN") redirect("/home");

  const matches = await getAdminMatches();
  const t = await getTranslations("admin");

  const grouped = groupByRound(matches);

  return (
    <main className="flex min-h-screen flex-col pb-24">
      <TopBar title={t("resultsTitle")} meta={me.displayName.toUpperCase()} />

      <div className="mx-auto w-full max-w-md sm:max-w-2xl lg:max-w-4xl">
        <p className="mx-3 mt-4 font-sans text-sm text-[var(--color-text-muted)]">
          {t("resultsSubtitle")}
        </p>

        {grouped.map(({ roundCode, roundName, rows }) => (
          <section key={roundCode} className="mx-3 mt-5">
            <h2 className="font-display text-lg font-extrabold uppercase tracking-tight">
              {roundName}
            </h2>
            <div className="mt-2 flex flex-col gap-2">
              {rows.map((m) => (
                <MatchResultRow key={m.matchId} match={m} timeZone={me.timezone} />
              ))}
            </div>
          </section>
        ))}
      </div>

      <BottomNav />
    </main>
  );
}

function groupByRound(matches: AdminMatchRow[]) {
  const map = new Map<string, { roundCode: string; roundName: string; sequence: number; rows: AdminMatchRow[] }>();
  for (const m of matches) {
    const key = m.roundCode;
    if (!map.has(key)) {
      map.set(key, {
        roundCode: m.roundCode,
        roundName: m.roundName,
        sequence: m.roundSequence,
        rows: [],
      });
    }
    map.get(key)!.rows.push(m);
  }
  return Array.from(map.values()).sort((a, b) => a.sequence - b.sequence);
}

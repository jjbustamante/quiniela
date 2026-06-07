import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi } from "vitest";
import type { H2HView, H2HMatch } from "@/lib/api/compare";
import { H2HCompare } from "./H2HCompare";

vi.mock("next-intl/server", () => ({
  getTranslations: async () => (key: string, vars?: Record<string, unknown>) => {
    const map: Record<string, string> = {
      colMatch: "Partido", colYou: "Tú", colReal: "Real",
      summary: "{agree} de acuerdo, {differ} distintos",
      summaryWinning: "{agree}/{differ} · {points}",
      lockedTitle: "AÚN NO", lockedHelp: "se revela al cerrar", noRival: "Elige rival",
      chipGROUP: "Grupos", chipR32: "16vos",
    };
    let s = map[key] ?? key;
    if (vars) for (const [k, v] of Object.entries(vars)) s = s.replace(`{${k}}`, String(v));
    return s;
  },
}));

function h2hMatch(over: Partial<H2HMatch> & { matchId: number; roundCode: string; kickoffAt: string; state: H2HMatch["state"] }): H2HMatch {
  return {
    team1Code: "A", team1Flag: "🏳", team2Code: "B", team2Flag: "🏳",
    actualScoreT1: 1, actualScoreT2: 0, played: true, revealed: true,
    myScoreT1: 2, myScoreT2: 1, rivalScoreT1: 0, rivalScoreT2: 0,
    ...over,
  };
}

async function renderH2H(data: H2HView) {
  const ui = await H2HCompare({ data });
  return render(<NextIntlClientProvider locale="es-CO" messages={{}}>{ui}</NextIntlClientProvider>);
}

describe("H2HCompare", () => {
  it("groups by stage (most-recent first, first open) with differ before agree in a stage", async () => {
    await renderH2H({
      rivalUserId: 7, rivalDisplayName: "Rival", agreeCount: 1, differCount: 2,
      myPoints: null, rivalPoints: null,
      matches: [
        h2hMatch({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-01T15:00:00Z", state: "differ" }),
        h2hMatch({ matchId: 2, roundCode: "R32", kickoffAt: "2026-06-05T15:00:00Z", state: "agree", team1Code: "AG" }),
        h2hMatch({ matchId: 3, roundCode: "R32", kickoffAt: "2026-06-05T18:00:00Z", state: "differ", team1Code: "DF" }),
      ],
    });
    const headers = screen.getAllByTestId("stage-header").map((el) => el.textContent);
    expect(headers).toEqual(["16vos", "Grupos"]);
    const sections = document.querySelectorAll("details");
    expect(sections[0]).toHaveAttribute("open");
    // In the R32 section, the differ row (DF) comes before the agree row (AG).
    const r32 = sections[0] as HTMLElement;
    const text = within(r32).getAllByRole("row").map((r) => r.textContent).join(" | ");
    expect(text.indexOf("DF")).toBeLessThan(text.indexOf("AG"));
  });
});

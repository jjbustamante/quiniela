import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect } from "vitest";
import type { H2HView, H2HMatch } from "@/lib/api/compare";
import { H2HCompare } from "./H2HCompare";

const messages = {
  compare: {
    colMatch: "Partido", colYou: "Tú", colReal: "Real",
    summary: "{agree} de acuerdo, {differ} distintos",
    summaryWinning: "{agree}/{differ} · {points}",
    lockedTitle: "AÚN NO", lockedHelp: "se revela al cerrar", noRival: "Elige rival",
    viewByDate: "Por fecha", viewByStage: "Por fase",
    tabPast: "Pasados", tabToday: "Hoy", tabUpcoming: "Próximos",
    emptyPast: "Sin pasados", emptyToday: "Sin partidos hoy", emptyUpcoming: "Sin próximos",
  },
  home: { chipGROUP: "Grupos", chipR32: "16vos" },
};

function h2hMatch(over: Partial<H2HMatch> & { matchId: number; roundCode: string; kickoffAt: string; state: H2HMatch["state"] }): H2HMatch {
  return {
    team1Code: "A", team1Flag: "🏳", team2Code: "B", team2Flag: "🏳",
    actualScoreT1: 1, actualScoreT2: 0, played: true, revealed: true,
    myScoreT1: 2, myScoreT2: 1, rivalScoreT1: 0, rivalScoreT2: 0,
    ...over,
  };
}

function renderH2H(data: H2HView | null) {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <H2HCompare data={data} />
    </NextIntlClientProvider>,
  );
}

const base: H2HView = {
  rivalUserId: 7, rivalDisplayName: "Rival",
  agreeCount: 1, differCount: 2,
  myPoints: null, rivalPoints: null,
  serverTime: "2026-06-16T12:00:00Z",
  past: [], today: [], upcoming: [],
};

describe("H2HCompare", () => {
  it("shows noRival when data is null", () => {
    renderH2H(null);
    expect(screen.getByText("Elige rival")).toBeInTheDocument();
  });

  it("shows lockedTitle when all buckets are empty", () => {
    renderH2H(base);
    expect(screen.getByText("AÚN NO")).toBeInTheDocument();
  });

  it("shows global summary line", () => {
    renderH2H({
      ...base,
      today: [h2hMatch({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-16T15:00:00Z", state: "agree" })],
    });
    expect(screen.getByText("1 de acuerdo, 2 distintos")).toBeInTheDocument();
  });

  it("shows summaryWinning when points are available", () => {
    renderH2H({
      ...base,
      myPoints: 10, rivalPoints: 8,
      today: [h2hMatch({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-16T15:00:00Z", state: "agree" })],
    });
    expect(screen.getByText("1/2 · 10–8")).toBeInTheDocument();
  });

  it("defaults to today and lists the rival's differing picks first", () => {
    renderH2H({
      ...base,
      today: [
        h2hMatch({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-16T15:00:00Z", state: "agree", team1Code: "AG" }),
        h2hMatch({ matchId: 2, roundCode: "GROUP", kickoffAt: "2026-06-16T18:00:00Z", state: "differ", team1Code: "DF" }),
      ],
    });
    // Today tab is active by default
    expect(screen.getByRole("button", { name: /hoy/i })).toBeInTheDocument();
    // Both rows are visible (in date mode, today tab)
    const rows = screen.getAllByRole("row");
    // Header row + 2 data rows
    expect(rows.length).toBeGreaterThanOrEqual(3);
    // differ row (DF) comes before agree row (AG)
    const text = rows.map((r) => r.textContent).join(" | ");
    expect(text.indexOf("DF")).toBeLessThan(text.indexOf("AG"));
    // differ row has highlight bg
    const allRows = document.querySelectorAll("tr");
    const dataRows = Array.from(allRows).filter((r) => r.querySelector("td"));
    const dfRow = dataRows.find((r) => r.textContent?.includes("DF"));
    expect(dfRow?.className).toContain("bg-[#fff4d6]");
  });

  it("switches to stage mode", async () => {
    renderH2H({
      ...base,
      past: [h2hMatch({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-01T15:00:00Z", state: "differ" })],
      today: [h2hMatch({ matchId: 2, roundCode: "R32", kickoffAt: "2026-06-16T15:00:00Z", state: "agree" })],
    });
    await userEvent.click(screen.getByRole("button", { name: /por fase/i }));
    const headers = screen.getAllByTestId("stage-header").map((el) => el.textContent);
    expect(headers).toEqual(["16vos", "Grupos"]);
  });

  it("groups by stage with differ before agree in stage mode", async () => {
    renderH2H({
      ...base,
      past: [
        h2hMatch({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-01T15:00:00Z", state: "differ" }),
        h2hMatch({ matchId: 2, roundCode: "R32", kickoffAt: "2026-06-05T15:00:00Z", state: "agree", team1Code: "AG" }),
        h2hMatch({ matchId: 3, roundCode: "R32", kickoffAt: "2026-06-05T18:00:00Z", state: "differ", team1Code: "DF" }),
      ],
    });
    await userEvent.click(screen.getByRole("button", { name: /por fase/i }));
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

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import type { MatchesView, MatchView } from "@/lib/api/matches";
import { MatchTabs } from "./MatchTabs";

function match(over: Partial<MatchView> & { id: number; roundCode: string; kickoffAt: string }): MatchView {
  return {
    groupCode: null,
    team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" },
    team2: { code: "MEX", name: "México", flag: "🇲🇽" },
    score: { t1: 2, t2: 1 },
    played: true,
    yourPick: null,
    pointsEarned: null,
    pickWinner: null,
    winner: null,
    ...over,
  };
}

const view: MatchesView = {
  serverTime: "2026-06-29T12:00:00Z",
  past: [
    match({ id: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z" }),
    match({ id: 2, roundCode: "R32", kickoffAt: "2026-06-28T15:00:00Z" }),
  ],
  today: [],
  upcoming: [],
};

const messages = {
  matches: {
    tabPast: "Pasados", tabToday: "Hoy", tabUpcoming: "Próximos",
    emptyPast: "—", emptyToday: "—", emptyUpcoming: "—",
    yourPick: "TU PICK", result: "RESULTADO", noPick: "—", liveDot: "EN VIVO",
    pointsEarned: "+{n} PTS", groupLabel: "GRUPO {code}",
    viewByDate: "Por fecha", viewByStage: "Por fase",
  },
  home: { chipGROUP: "Grupos", chipR32: "16vos" },
};

function renderTabs() {
  render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <MatchTabs view={view} timeZone="America/Bogota" />
    </NextIntlClientProvider>,
  );
}

describe("MatchTabs", () => {
  it("shows the date tabs by default", () => {
    renderTabs();
    expect(screen.getByRole("button", { name: /pasados/i })).toBeInTheDocument();
    expect(screen.queryByTestId("stage-header")).not.toBeInTheDocument();
  });

  it("switches to collapsible stage sections, most-recent first, first open", async () => {
    renderTabs();
    await userEvent.click(screen.getByRole("button", { name: /por fase/i }));
    const headers = screen.getAllByTestId("stage-header").map((el) => el.textContent);
    expect(headers).toEqual(["16vos", "Grupos"]);
    // Most-recent stage (R32 = 16vos) starts expanded; the older one collapsed.
    const sections = document.querySelectorAll("details");
    expect(sections[0]).toHaveAttribute("open");
    expect(sections[1]).not.toHaveAttribute("open");
  });
});

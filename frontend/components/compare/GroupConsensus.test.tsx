import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect } from "vitest";
import type { GroupConsensusView, MatchConsensus } from "@/lib/api/compare";
import { GroupConsensus } from "./GroupConsensus";

const messages = {
  compare: {
    lockedTitle: "AÚN NO", lockedHelp: "se revela al cerrar",
    majority: "Con la mayoría", rebel: "Rebelde", youTag: "tú",
    viewByDate: "Por fecha", viewByStage: "Por fase",
    tabPast: "Pasados", tabToday: "Hoy", tabUpcoming: "Próximos",
    emptyPast: "—", emptyToday: "Sin partidos hoy", emptyUpcoming: "—",
    rivalsAbove: "{n} por encima de ti", rivalsAboveWith: "{n} contigo",
    picksTitle: "Quién eligió qué", picksAboveOnly: "Por encima de ti",
    picksClose: "Cerrar", colPoints: "Pts",
  },
  home: { chipGROUP: "Grupos", chipR32: "16vos" },
};

function card(over: Partial<MatchConsensus> & { matchId: number; roundCode: string; kickoffAt: string }): MatchConsensus {
  return {
    team1Code: "BRA", team1Flag: "🇧🇷", team2Code: "SRB", team2Flag: "🇷🇸",
    actualScoreT1: null, actualScoreT2: null, played: true, revealed: true,
    myScoreT1: 1, myScoreT2: 0,
    distribution: [{ scoreT1: 1, scoreT2: 0, count: 2, rivalsAboveCount: 0 }],
    totalPicks: 2, majority: true, rebel: false,
    rivalsAboveTotal: 0, rivalsAbovePicked: 0,
    ...over,
  };
}

function renderGC(data: GroupConsensusView) {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <GroupConsensus data={data} />
    </NextIntlClientProvider>,
  );
}

const base: GroupConsensusView = { serverTime: "2026-06-16T12:00:00Z", past: [], today: [], upcoming: [] };

describe("GroupConsensus", () => {
  it("defaults to the Today tab in date mode", () => {
    renderGC({ ...base, today: [card({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-16T15:00:00Z" })] });
    expect(screen.getByRole("button", { name: /hoy/i })).toBeInTheDocument();
    expect(screen.getByText("1–0")).toBeInTheDocument();
    expect(screen.queryByTestId("stage-header")).not.toBeInTheDocument();
  });

  it("shows an empty-today message when today is empty", () => {
    renderGC({ ...base, upcoming: [card({ matchId: 2, roundCode: "GROUP", kickoffAt: "2026-06-20T15:00:00Z" })] });
    // initial tab falls back to upcoming when today is empty (mirrors MatchTabs)
    expect(screen.getByText("1–0")).toBeInTheDocument();
  });

  it("switches to stage sections", async () => {
    renderGC({
      ...base,
      past: [card({ matchId: 1, roundCode: "GROUP", kickoffAt: "2026-06-01T15:00:00Z" })],
      today: [card({ matchId: 2, roundCode: "R32", kickoffAt: "2026-06-16T15:00:00Z" })],
    });
    await userEvent.click(screen.getByRole("button", { name: /por fase/i }));
    const headers = screen.getAllByTestId("stage-header").map((el) => el.textContent);
    expect(headers).toEqual(["16vos", "Grupos"]);
  });

  it("shows the locked state when everything is empty", () => {
    renderGC(base);
    expect(screen.getByText("AÚN NO")).toBeInTheDocument();
  });
});

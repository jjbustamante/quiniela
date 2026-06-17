import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi } from "vitest";
import type { MatchConsensus, MatchPicksView } from "@/lib/api/compare";
import { ConsensusCard } from "./ConsensusCard";

vi.mock("@/lib/actions/compare-picks", () => ({
  fetchMatchPicks: vi.fn(async (): Promise<MatchPicksView> => ({
    matchId: 1, actualScoreT1: null, actualScoreT2: null, played: false,
    picks: [
      { displayName: "Carlos", rank: 1, points: 99, isYou: false, isBot: false, isAboveMe: true, scoreT1: 2, scoreT2: 0, pointsEarned: null },
      { displayName: "Tú", rank: 9, points: 0, isYou: true, isBot: false, isAboveMe: false, scoreT1: 2, scoreT2: 1, pointsEarned: null },
    ],
  })),
}));

const messages = {
  compare: {
    rebel: "Rebelde", majority: "Con la mayoría", youTag: "tú",
    rivalsAbove: "{n} por encima de ti", rivalsAboveWith: "{n} contigo",
    picksTitle: "Quién eligió qué", picksAboveOnly: "Por encima de ti",
    picksClose: "Cerrar", colPoints: "Pts",
  },
};

function wrap(m: MatchConsensus) {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <ConsensusCard m={m} />
    </NextIntlClientProvider>,
  );
}

const m: MatchConsensus = {
  matchId: 1, roundCode: "GROUP", team1Code: "MEX", team1Flag: "🇲🇽", team2Code: "RSA", team2Flag: "🇿🇦",
  kickoffAt: "2026-06-16T15:00:00Z", actualScoreT1: null, actualScoreT2: null, played: true, revealed: true,
  myScoreT1: 2, myScoreT2: 1,
  distribution: [
    { scoreT1: 2, scoreT2: 1, count: 5, rivalsAboveCount: 2 },
    { scoreT1: 2, scoreT2: 0, count: 13, rivalsAboveCount: 3 },
  ],
  totalPicks: 18, majority: false, rebel: false,
  rivalsAboveTotal: 5, rivalsAbovePicked: 5,
};

describe("ConsensusCard drill-down", () => {
  it("loads and shows named picks when a bar is tapped", async () => {
    wrap(m);
    await userEvent.click(screen.getByRole("button", { name: /2–0/ }));
    expect(await screen.findByText("Carlos")).toBeInTheDocument();
  });

  it("filters to rivals-above when the ↑ mark is tapped", async () => {
    wrap(m);
    const marks = screen.getAllByTestId("rivals-above-mark");
    await userEvent.click(marks[0]);
    expect(await screen.findByText("Carlos")).toBeInTheDocument();
    // 'Tú' is not above me, so filtered out of the above-only view
    expect(screen.queryByText("Tú")).not.toBeInTheDocument();
  });
});

describe("ConsensusCard rivals-above", () => {
  it("shows the match-level rivals-above summary", () => {
    wrap(m);
    expect(screen.getByText("5 por encima de ti")).toBeInTheDocument();
  });

  it("marks bars where rivals-above picked that score", () => {
    wrap(m);
    expect(screen.getAllByTestId("rivals-above-mark").length).toBe(2);
  });

  it("renders no rivals-above UI when the caller is on top", () => {
    wrap({ ...m, rivalsAboveTotal: 0, rivalsAbovePicked: 0,
      distribution: m.distribution.map((s) => ({ ...s, rivalsAboveCount: 0 })) });
    expect(screen.queryByTestId("rivals-above-mark")).not.toBeInTheDocument();
  });
});

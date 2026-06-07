import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { MatchListItem } from "./MatchListItem";
import type { MatchView } from "@/lib/api/matches";

function match(overrides: Partial<MatchView> = {}): MatchView {
  return {
    id: 1,
    roundCode: "R32",
    groupCode: null,
    kickoffAt: "2026-06-29T15:30:00Z",
    team1: { code: "CUW", name: "Curaçao", flag: "🇨🇼" },
    team2: { code: "PAR", name: "Paraguay", flag: "🇵🇾" },
    score: { t1: 1, t2: 1 },
    played: true,
    yourPick: { t1: 1, t2: 1 },
    pointsEarned: 4,
    breakdown: { outcome: 3, team1Exact: 0, team2Exact: 0, goalDiff: 1, multiplier: 1, total: 4 },
    pickWinner: null,
    winner: null,
    ...overrides,
  };
}

const labels = {
  yourPick: "TU PICK",
  result: "RESULTADO",
  noPick: "—",
  live: "EN VIVO",
  formatPoints: (n: number) => `+${n} PTS`,
  kickoff: "29 JUN · 15:30",
  groupLabel: null,
  roundLabel: "16vos",
  toggleBreakdown: "Ver desglose",
  breakdown: {
    bdOutcome: "resultado",
    bdTeam1: "local",
    bdTeam2: "visitante",
    bdDiff: "diferencia",
    multiplier: (n: number) => `×${n}`,
    pts: (n: number) => `+${n}`,
  },
};

describe("MatchListItem — round label", () => {
  it("shows the localized round label, not the raw round code", () => {
    render(
      <MatchListItem
        match={match({ roundCode: "R32" })}
        labels={labels}
        showResult
        now={Date.parse("2026-06-30T00:00:00Z")}
      />,
    );
    expect(screen.getByText("16vos")).toBeInTheDocument();
    expect(screen.queryByText("R32")).not.toBeInTheDocument();
  });
});

describe("MatchListItem — knockout draw pick winner", () => {
  it("shows the predicted advancing team when the pick is a draw", () => {
    render(
      <MatchListItem
        match={match({
          score: { t1: 0, t2: 1 },
          pickWinner: { code: "CUW", name: "Curaçao", flag: "🇨🇼" },
        })}
        labels={labels}
        showResult
        now={Date.parse("2026-06-30T00:00:00Z")}
      />,
    );
    // Curaçao appears once in its team cell and again as the picked winner.
    expect(screen.getAllByText(/Curaçao/i).length).toBeGreaterThan(1);
  });

  it("does not surface a pick-winner chip when the pick is decisive", () => {
    render(
      <MatchListItem
        match={match({
          yourPick: { t1: 2, t2: 0 },
          pickWinner: { code: "CUW", name: "Curaçao", flag: "🇨🇼" },
        })}
        labels={labels}
        showResult
        now={Date.parse("2026-06-30T00:00:00Z")}
      />,
    );
    // Curaçao only appears in its own team cell — no extra picked-winner chip.
    expect(screen.getAllByText(/Curaçao/i).length).toBe(1);
  });

  it("shows the actual advancing team when a knockout result is a draw", () => {
    render(
      <MatchListItem
        match={match({
          score: { t1: 1, t2: 1 },
          winner: { code: "PAR", name: "Paraguay", flag: "🇵🇾" },
        })}
        labels={labels}
        showResult
        now={Date.parse("2026-06-30T00:00:00Z")}
      />,
    );
    expect(screen.getAllByText(/Paraguay/i).length).toBeGreaterThan(1);
  });

  it("does not show an advancing chip for a decisive result", () => {
    render(
      <MatchListItem
        match={match({
          score: { t1: 2, t2: 1 },
          winner: { code: "PAR", name: "Paraguay", flag: "🇵🇾" },
        })}
        labels={labels}
        showResult
        now={Date.parse("2026-06-30T00:00:00Z")}
      />,
    );
    expect(screen.getAllByText(/Paraguay/i).length).toBe(1);
  });
});

describe("MatchListItem — breakdown toggle", () => {
  it("reveals the breakdown line on tapping the points badge", async () => {
    render(
      <MatchListItem match={match()} labels={labels} showResult now={Date.parse("2026-06-30T00:00:00Z")} />,
    );
    expect(screen.queryByText(/resultado \+3/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /ver desglose/i }));
    expect(screen.getByText(/resultado \+3/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /ver desglose/i }));
    expect(screen.queryByText(/resultado \+3/)).not.toBeInTheDocument();
  });

  it("renders no badge when there are no points", () => {
    render(
      <MatchListItem match={match({ pointsEarned: null, breakdown: null })} labels={labels} showResult now={Date.parse("2026-06-30T00:00:00Z")} />,
    );
    expect(screen.queryByRole("button", { name: /ver desglose/i })).not.toBeInTheDocument();
  });
});

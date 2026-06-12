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
    live: false,
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

describe("MatchListItem — live match", () => {
  it("shows the live score with the EN VIVO indicator for a live match", () => {
    render(
      <MatchListItem
        match={match({
          played: false,
          live: true,
          score: { t1: 2, t2: 0 },
          pointsEarned: 7,
          breakdown: { outcome: 3, team1Exact: 2, team2Exact: 2, goalDiff: 0, multiplier: 1, total: 7 },
        })}
        labels={labels}
        showResult
        now={Date.parse("2026-06-29T16:00:00Z")}
      />,
    );
    // Live score is shown
    expect(screen.getByText("2–0")).toBeInTheDocument();
    // EN VIVO indicator is shown
    expect(screen.getByText("EN VIVO")).toBeInTheDocument();
    // Points badge is rendered (provisional)
    expect(screen.getByRole("button", { name: /ver desglose/i })).toBeInTheDocument();
    expect(screen.getByText("+7 PTS")).toBeInTheDocument();
  });

  it("provisional points badge is visually distinct from a played match (has a live dot inside)", () => {
    const { container: liveContainer } = render(
      <MatchListItem
        match={match({
          played: false,
          live: true,
          score: { t1: 1, t2: 0 },
          pointsEarned: 3,
          breakdown: { outcome: 3, team1Exact: 0, team2Exact: 0, goalDiff: 0, multiplier: 1, total: 3 },
        })}
        labels={labels}
        showResult
        now={Date.parse("2026-06-29T16:00:00Z")}
      />,
    );

    const { container: playedContainer } = render(
      <MatchListItem
        match={match({ played: true, live: false, score: { t1: 1, t2: 0 }, pointsEarned: 3 })}
        labels={labels}
        showResult
        now={Date.parse("2026-06-29T16:00:00Z")}
      />,
    );

    const liveBtn = liveContainer.querySelector("button[aria-expanded]");
    const playedBtn = playedContainer.querySelector("button[aria-expanded]");
    // Live badge uses red background class; played badge uses gold
    expect(liveBtn?.className).toContain("color-accent-red");
    expect(playedBtn?.className).toContain("color-accent-gold");
  });

  it("upcoming match with no kickoff yet shows VS, not EN VIVO", () => {
    render(
      <MatchListItem
        match={match({
          played: false,
          live: false,
          score: null,
          pointsEarned: null,
          breakdown: null,
          kickoffAt: "2026-07-01T15:30:00Z",
        })}
        labels={labels}
        showResult={false}
        now={Date.parse("2026-06-29T16:00:00Z")}
      />,
    );
    expect(screen.getByText("VS")).toBeInTheDocument();
    expect(screen.queryByText("EN VIVO")).not.toBeInTheDocument();
  });
});

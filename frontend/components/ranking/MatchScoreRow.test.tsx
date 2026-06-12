import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { MatchScore } from "@/lib/api/scorecard";
import { MatchScoreRow } from "./MatchScoreRow";

function match(over: Partial<MatchScore> = {}): MatchScore {
  return {
    matchId: 1,
    team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" },
    team2: { code: "MEX", name: "México", flag: "🇲🇽" },
    kickoffAt: "2026-06-28T15:00:00Z",
    betScoreT1: 2,
    betScoreT2: 1,
    actualScoreT1: 2,
    actualScoreT2: 1,
    points: 14,
    breakdown: { outcome: 3, team1Exact: 2, team2Exact: 2, goalDiff: 0, multiplier: 2, total: 14 },
    ...over,
  };
}

const NOTE_PLACED = "Registraste esta predicción después de que terminó el partido, por eso no suma puntos.";
const NOTE_EDITED = "Editaste esta predicción después de que terminó el partido; se conservó tu puntaje original.";

const labels = {
  pick: "PICK",
  result: "REAL",
  bdOutcome: "resultado",
  bdTeam1: "local",
  bdTeam2: "visitante",
  bdDiff: "diferencia",
  multiplier: (n: number) => `×${n}`,
  pts: (n: number) => `+${n}`,
  noteText: (key: "PLACED_AFTER_KICKOFF" | "EDITED_AFTER_KICKOFF") =>
    key === "PLACED_AFTER_KICKOFF" ? NOTE_PLACED : NOTE_EDITED,
};

describe("MatchScoreRow", () => {
  it("shows the total, the contributing components, and the multiplier", () => {
    render(<MatchScoreRow match={match()} labels={labels} />);
    expect(screen.getByText("+14")).toBeInTheDocument();
    expect(screen.getByText(/resultado \+3/)).toBeInTheDocument();
    expect(screen.getByText(/local \+2/)).toBeInTheDocument();
    expect(screen.getByText(/visitante \+2/)).toBeInTheDocument();
    expect(screen.getByText(/×2/)).toBeInTheDocument();
    expect(screen.queryByText(/diferencia/)).not.toBeInTheDocument();
  });

  it("omits the breakdown line for a zero-point match", () => {
    render(
      <MatchScoreRow
        match={match({
          points: 0,
          breakdown: { outcome: 0, team1Exact: 0, team2Exact: 0, goalDiff: 0, multiplier: 1, total: 0 },
        })}
        labels={labels}
      />,
    );
    expect(screen.getByText("+0")).toBeInTheDocument();
    expect(screen.queryByText(/resultado/)).not.toBeInTheDocument();
  });

  it("hides the multiplier on a zero-point knockout (no lone ×3)", () => {
    render(
      <MatchScoreRow
        match={match({
          points: 0,
          breakdown: { outcome: 0, team1Exact: 0, team2Exact: 0, goalDiff: 0, multiplier: 3, total: 0 },
        })}
        labels={labels}
      />,
    );
    expect(screen.getByText("+0")).toBeInTheDocument();
    expect(screen.queryByText(/×3/)).not.toBeInTheDocument();
  });

  it("shows frozen points (0), the note, and hides the live breakdown for a placed-after bet", () => {
    // Frozen 0, but the current prediction would recompute to 5 — the badge must show the frozen 0,
    // not the phantom live total, and the live breakdown is suppressed.
    render(
      <MatchScoreRow
        match={match({
          points: 0,
          breakdown: { outcome: 3, team1Exact: 2, team2Exact: 0, goalDiff: 0, multiplier: 1, total: 5 },
          note: "PLACED_AFTER_KICKOFF",
        })}
        labels={labels}
      />,
    );
    expect(screen.getByText("+0")).toBeInTheDocument();
    expect(screen.queryByText("+5")).not.toBeInTheDocument();
    expect(screen.getByText(NOTE_PLACED)).toBeInTheDocument();
    expect(screen.queryByText(/resultado/)).not.toBeInTheDocument();
  });

  it("shows frozen points (original), the note, and hides the live breakdown for an edited-after bet", () => {
    // Frozen 4 (original), current prediction recomputes to 7 — badge shows 4, breakdown hidden.
    render(
      <MatchScoreRow
        match={match({
          points: 4,
          breakdown: { outcome: 3, team1Exact: 2, team2Exact: 2, goalDiff: 0, multiplier: 1, total: 7 },
          note: "EDITED_AFTER_KICKOFF",
        })}
        labels={labels}
      />,
    );
    expect(screen.getByText("+4")).toBeInTheDocument();
    expect(screen.queryByText("+7")).not.toBeInTheDocument();
    expect(screen.getByText(NOTE_EDITED)).toBeInTheDocument();
    expect(screen.queryByText(/resultado/)).not.toBeInTheDocument();
  });

  it("renders no note when note is absent", () => {
    render(<MatchScoreRow match={match()} labels={labels} />);
    expect(screen.queryByText(NOTE_PLACED)).not.toBeInTheDocument();
    expect(screen.queryByText(NOTE_EDITED)).not.toBeInTheDocument();
  });
});

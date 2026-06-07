import { describe, expect, it } from "vitest";
import { breakdownParts } from "./breakdown-format";

const labels = {
  bdOutcome: "resultado",
  bdTeam1: "local",
  bdTeam2: "visitante",
  bdDiff: "diferencia",
  multiplier: (n: number) => `×${n}`,
  pts: (n: number) => `+${n}`,
};

describe("breakdownParts", () => {
  it("lists only the non-zero components, then the multiplier when > 1", () => {
    expect(
      breakdownParts(
        { outcome: 3, team1Exact: 2, team2Exact: 2, goalDiff: 0, multiplier: 2, total: 14 },
        labels,
      ),
    ).toEqual(["resultado +3", "local +2", "visitante +2", "×2"]);
  });

  it("omits the multiplier when it is 1", () => {
    expect(
      breakdownParts(
        { outcome: 3, team1Exact: 0, team2Exact: 0, goalDiff: 1, multiplier: 1, total: 4 },
        labels,
      ),
    ).toEqual(["resultado +3", "diferencia +1"]);
  });

  it("returns [] for an all-zero breakdown", () => {
    expect(
      breakdownParts(
        { outcome: 0, team1Exact: 0, team2Exact: 0, goalDiff: 0, multiplier: 1, total: 0 },
        labels,
      ),
    ).toEqual([]);
  });

  it("omits the multiplier when nothing scored (a bare ×N is meaningless)", () => {
    expect(
      breakdownParts(
        { outcome: 0, team1Exact: 0, team2Exact: 0, goalDiff: 0, multiplier: 3, total: 0 },
        labels,
      ),
    ).toEqual([]);
  });
});

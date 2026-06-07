import { describe, expect, it } from "vitest";
import { groupMatchesByStage } from "./matches-by-stage";
import type { MatchView } from "@/lib/api/matches";

function m(over: Partial<MatchView> & { id: number; roundCode: string; kickoffAt: string }): MatchView {
  return {
    groupCode: null,
    team1: { code: "A", name: "A", flag: "🏳" },
    team2: { code: "B", name: "B", flag: "🏳" },
    score: null,
    played: false,
    yourPick: null,
    pointsEarned: null,
    pickWinner: null,
    winner: null,
    ...over,
  };
}

const NOW = Date.parse("2026-06-29T12:00:00Z");

describe("groupMatchesByStage", () => {
  it("returns [] for no matches", () => {
    expect(groupMatchesByStage([], NOW)).toEqual([]);
  });

  it("groups matches by roundCode", () => {
    const groups = groupMatchesByStage(
      [
        m({ id: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z" }),
        m({ id: 2, roundCode: "GROUP", kickoffAt: "2026-06-12T15:00:00Z" }),
        m({ id: 3, roundCode: "R32", kickoffAt: "2026-06-28T15:00:00Z" }),
      ],
      NOW,
    );
    const group = groups.find((g) => g.roundCode === "GROUP")!;
    expect(group.matches.map((x) => x.id)).toEqual([1, 2]);
    expect(groups.find((g) => g.roundCode === "R32")!.matches.map((x) => x.id)).toEqual([3]);
  });

  it("orders started stages most-recent first (16vos above grupos after the group phase)", () => {
    const groups = groupMatchesByStage(
      [
        m({ id: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z" }),
        m({ id: 2, roundCode: "R32", kickoffAt: "2026-06-28T15:00:00Z" }),
      ],
      NOW,
    );
    expect(groups.map((g) => g.roundCode)).toEqual(["R32", "GROUP"]);
  });

  it("places not-yet-started stages after started ones, soonest first", () => {
    const groups = groupMatchesByStage(
      [
        m({ id: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z" }),
        m({ id: 3, roundCode: "QF", kickoffAt: "2026-07-02T15:00:00Z" }),
        m({ id: 2, roundCode: "R16", kickoffAt: "2026-06-30T15:00:00Z" }),
      ],
      NOW,
    );
    expect(groups.map((g) => g.roundCode)).toEqual(["GROUP", "R16", "QF"]);
  });

  it("sorts matches within a stage chronologically", () => {
    const groups = groupMatchesByStage(
      [
        m({ id: 2, roundCode: "GROUP", kickoffAt: "2026-06-12T15:00:00Z" }),
        m({ id: 1, roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z" }),
      ],
      NOW,
    );
    expect(groups[0].matches.map((x) => x.id)).toEqual([1, 2]);
  });

  it("works on any object with roundCode + kickoffAt (generic)", () => {
    const groups = groupMatchesByStage(
      [
        { roundCode: "GROUP", kickoffAt: "2026-06-11T15:00:00Z", label: "g" },
        { roundCode: "R32", kickoffAt: "2026-06-28T15:00:00Z", label: "k" },
      ],
      Date.parse("2026-06-29T12:00:00Z"),
    );
    expect(groups.map((g) => g.roundCode)).toEqual(["R32", "GROUP"]);
    expect(groups[0].matches[0].label).toBe("k");
  });
});

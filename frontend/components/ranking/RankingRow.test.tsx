import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RankingRow } from "./RankingRow";
import type { RankingEntry } from "@/lib/api/ranking";

function entry(overrides: Partial<RankingEntry> = {}): RankingEntry {
  return {
    rank: 1,
    userId: 1,
    displayName: "Pulpo Paul 🐙",
    points: 50,
    delta: null,
    isYou: false,
    isBot: false,
    ...overrides,
  };
}

const rowProps = {
  youLabel: "TÚ",
  trendUp: "▲",
  trendDown: "▼",
  trendFlat: "—",
};

describe("RankingRow", () => {
  it("shows an exhibition badge for bot entries", () => {
    render(<RankingRow entry={entry({ isBot: true })} {...rowProps} />);
    expect(screen.getByText(/fuera de premio/i)).toBeInTheDocument();
  });

  it("does not show the badge for normal players", () => {
    render(<RankingRow entry={entry({ isBot: false, displayName: "Juan" })} {...rowProps} />);
    expect(screen.queryByText(/fuera de premio/i)).not.toBeInTheDocument();
  });
});

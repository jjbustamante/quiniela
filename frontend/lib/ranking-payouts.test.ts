import { describe, expect, it } from "vitest";
import { buildPayoutLabels, computePrizeRanks } from "./ranking-payouts";
import type { RankingEntry } from "@/lib/api/ranking";
import type { PrizeSplitEntry } from "@/lib/api/summary";

function entry(overrides: Partial<RankingEntry> = {}): RankingEntry {
  return {
    rank: 1,
    userId: 1,
    displayName: "Jugador",
    points: 10,
    delta: null,
    isYou: false,
    isBot: false,
    ...overrides,
  };
}

const split: PrizeSplitEntry[] = [
  { rank: 1, percentage: 80, payoutCents: 20800 },
  { rank: 2, percentage: 15, payoutCents: 3900 },
  { rank: 3, percentage: 5, payoutCents: 1300 },
];

describe("buildPayoutLabels", () => {
  it("shows no labels when nobody has scored (all points 0)", () => {
    const entries = [
      entry({ userId: 1, rank: 1, points: 0 }),
      entry({ userId: 2, rank: 1, points: 0 }),
      entry({ userId: 3, rank: 1, points: 0 }),
    ];
    const labels = buildPayoutLabels(entries, split, "USD");
    expect(labels.size).toBe(0);
  });

  it("shows medal + amount for distinct top-3, keyed by userId", () => {
    const entries = [
      entry({ userId: 1, rank: 1, points: 30 }),
      entry({ userId: 2, rank: 2, points: 20 }),
      entry({ userId: 3, rank: 3, points: 10 }),
    ];
    const labels = buildPayoutLabels(entries, split, "USD");
    expect(labels.get(1)).toBe("🥇 $208");
    expect(labels.get(2)).toBe("🥈 $39");
    expect(labels.get(3)).toBe("🥉 $13");
  });

  it("shows the medal only (no amount) for a tied prize rank", () => {
    const entries = [
      entry({ userId: 1, rank: 1, points: 30 }),
      entry({ userId: 2, rank: 1, points: 30 }),
      entry({ userId: 3, rank: 3, points: 10 }),
    ];
    const labels = buildPayoutLabels(entries, split, "USD");
    expect(labels.get(1)).toBe("🥇");
    expect(labels.get(2)).toBe("🥇");
    // The untied bronze position still shows its amount.
    expect(labels.get(3)).toBe("🥉 $13");
  });

  it("ignores the bot when deciding whether standings are real", () => {
    const entries = [
      entry({ userId: 99, rank: 1, points: 50, isBot: true }),
      entry({ userId: 1, rank: 2, points: 0 }),
      entry({ userId: 2, rank: 2, points: 0 }),
    ];
    const labels = buildPayoutLabels(entries, split, "USD");
    expect(labels.size).toBe(0);
  });

  it("attaches the gold label to the real #1 human, not a bot sitting at raw rank 1", () => {
    // Mirrors prod: Pulpo Paul tops the raw leaderboard but is never
    // prize-eligible — the human directly below him is the real #1.
    const entries = [
      entry({ userId: 99, rank: 1, displayName: "Pulpo Paul 🐙", points: 664, isBot: true }),
      entry({ userId: 5, rank: 2, displayName: "José Manuel", points: 627 }),
      entry({ userId: 6, rank: 3, displayName: "Arturo", points: 620 }),
    ];
    const labels = buildPayoutLabels(entries, split, "USD");
    expect(labels.get(5)).toBe("🥇 $208");
    expect(labels.get(6)).toBe("🥈 $39");
    expect(labels.has(99)).toBe(false);
  });
});

describe("computePrizeRanks", () => {
  it("skips bots and assigns prize ranks based on position among humans only", () => {
    const entries = [
      entry({ userId: 99, rank: 1, points: 200, isBot: true }),
      entry({ userId: 1, rank: 2, points: 190 }),
      entry({ userId: 2, rank: 3, points: 180 }),
      entry({ userId: 3, rank: 4, points: 170 }),
    ];
    const groups = computePrizeRanks(entries, 3);
    expect(groups).toEqual([
      { rank: 1, entries: [entries[1]] },
      { rank: 2, entries: [entries[2]] },
      { rank: 3, entries: [entries[3]] },
    ]);
  });

  it("groups tied points under one rank and stops once the rank exceeds prizeRankCount", () => {
    const entries = [
      entry({ userId: 1, rank: 1, points: 190 }),
      entry({ userId: 2, rank: 2, points: 180 }),
      entry({ userId: 3, rank: 3, points: 170 }),
      entry({ userId: 4, rank: 3, points: 170 }),
      entry({ userId: 5, rank: 5, points: 160 }),
    ];
    const groups = computePrizeRanks(entries, 3);
    expect(groups).toEqual([
      { rank: 1, entries: [entries[0]] },
      { rank: 2, entries: [entries[1]] },
      { rank: 3, entries: [entries[2], entries[3]] },
    ]);
  });
});

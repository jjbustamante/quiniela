import { render, screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi, afterEach } from "vitest";
import type { MatchConsensus, MatchPicksView } from "@/lib/api/compare";
import { ConsensusCard } from "./ConsensusCard";

vi.mock("@/lib/actions/compare-picks", () => ({
  fetchMatchPicks: vi.fn(async (): Promise<MatchPicksView> => ({
    matchId: 1, actualScoreT1: null, actualScoreT2: null, played: false,
    picks: [
      // Two rivals-above who picked 2-1 → must appear when tapping the 2-1 ↑ mark
      { displayName: "Ana", rank: 1, points: 110, isYou: false, isBot: false, isAboveMe: true, scoreT1: 2, scoreT2: 1, pointsEarned: null },
      { displayName: "Luis", rank: 2, points: 105, isYou: false, isBot: false, isAboveMe: true, scoreT1: 2, scoreT2: 1, pointsEarned: null },
      // Rival-above who picked a DIFFERENT scoreline (2-0) → must be EXCLUDED when tapping 2-1 mark
      { displayName: "Carlos", rank: 3, points: 99, isYou: false, isBot: false, isAboveMe: true, scoreT1: 2, scoreT2: 0, pointsEarned: null },
      // Non-above picker on 2-1 → must be EXCLUDED when tapping 2-1 mark
      { displayName: "Pedro", rank: 8, points: 40, isYou: false, isBot: false, isAboveMe: false, scoreT1: 2, scoreT2: 1, pointsEarned: null },
      // The user themselves
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
  it("loads and shows all pickers on that scoreline when the 2-1 bar is tapped", async () => {
    wrap(m);
    await userEvent.click(screen.getByRole("button", { name: /2–1/ }));
    // Ana, Luis, Pedro, and Tú all picked 2-1 → all shown
    expect(await screen.findByText("Ana")).toBeInTheDocument();
    expect(screen.getByText("Luis")).toBeInTheDocument();
    expect(screen.getByText("Pedro")).toBeInTheDocument();
    // Carlos picked 2-0 → excluded
    expect(screen.queryByText("Carlos")).not.toBeInTheDocument();
  });

  it("filters to rivals-above on the 2-1 scoreline when the 2-1 ↑ mark is tapped", async () => {
    wrap(m);
    // The 2-1 row is first in distribution; find its mark explicitly by label
    const marks = screen.getAllByTestId("rivals-above-mark");
    // marks[0] = 2-1 row (first distribution entry), marks[1] = 2-0 row
    await userEvent.click(marks[0]);

    // Ana and Luis: above-me AND picked 2-1 → must appear
    expect(await screen.findByText("Ana")).toBeInTheDocument();
    expect(screen.getByText("Luis")).toBeInTheDocument();

    // Carlos: above-me but picked 2-0 → must NOT appear
    expect(screen.queryByText("Carlos")).not.toBeInTheDocument();

    // Pedro: picked 2-1 but NOT above-me → must NOT appear
    expect(screen.queryByText("Pedro")).not.toBeInTheDocument();

    // Tú: isYou, not above-me → must NOT appear
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

describe("ConsensusCard 403/null picks caching", () => {
  afterEach(() => cleanup());

  it("does not crash, shows panel header with no pick rows, and does not refetch on re-open", async () => {
    // Override mock to return null (403 / unrevealed case)
    const { fetchMatchPicks } = await import("@/lib/actions/compare-picks");
    const mockFetch = fetchMatchPicks as ReturnType<typeof vi.fn>;
    mockFetch.mockClear();
    mockFetch.mockResolvedValueOnce(null as unknown as MatchPicksView);

    wrap(m);
    const user = userEvent.setup();

    // Open panel for the first time
    await user.click(screen.getByRole("button", { name: /2–1/ }));

    // Panel header must appear (no crash)
    expect(await screen.findByText("Quién eligió qué")).toBeInTheDocument();

    // No pick rows rendered
    expect(screen.queryByRole("listitem")).not.toBeInTheDocument();

    // Call count after first open: 1
    expect(mockFetch).toHaveBeenCalledTimes(1);

    // Close the panel
    await user.click(screen.getByRole("button", { name: /Cerrar/i }));

    // Re-open same panel
    await user.click(screen.getByRole("button", { name: /2–1/ }));
    expect(await screen.findByText("Quién eligió qué")).toBeInTheDocument();

    // Must still be 1 — cached null, no second fetch
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });
});

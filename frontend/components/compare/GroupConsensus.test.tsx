import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi } from "vitest";
import type { GroupConsensusView } from "@/lib/api/compare";
import { GroupConsensus } from "./GroupConsensus";

vi.mock("next-intl/server", () => ({
  getTranslations: async () => (key: string) => {
    const map: Record<string, string> = {
      lockedTitle: "AÚN NO",
      lockedHelp: "se revela al cerrar",
      majority: "Con la mayoría",
      rebel: "Rebelde",
      youTag: "tú",
    };
    return map[key] ?? key;
  },
}));

const messages = {};

async function renderGC(data: GroupConsensusView) {
  const ui = await GroupConsensus({ data });
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      {ui}
    </NextIntlClientProvider>,
  );
}

describe("GroupConsensus", () => {
  it("shows the locked state when nothing is revealed", async () => {
    await renderGC({
      matches: [
        {
          matchId: 1,
          roundCode: "GROUP",
          team1Code: "BRA",
          team1Flag: "🇧🇷",
          team2Code: "SRB",
          team2Flag: "🇷🇸",
          kickoffAt: "2026-06-11T17:00:00Z",
          actualScoreT1: null,
          actualScoreT2: null,
          played: false,
          revealed: false,
          myScoreT1: 2,
          myScoreT2: 1,
          distribution: [],
          totalPicks: 0,
          majority: false,
          rebel: false,
        },
      ],
    });
    expect(screen.getByText("AÚN NO")).toBeInTheDocument();
  });

  it("renders a consensus bar and the rebel tag when revealed", async () => {
    await renderGC({
      matches: [
        {
          matchId: 1,
          roundCode: "GROUP",
          team1Code: "BRA",
          team1Flag: "🇧🇷",
          team2Code: "SRB",
          team2Flag: "🇷🇸",
          kickoffAt: "2026-06-11T17:00:00Z",
          actualScoreT1: null,
          actualScoreT2: null,
          played: false,
          revealed: true,
          myScoreT1: 4,
          myScoreT2: 4,
          distribution: [
            { scoreT1: 1, scoreT2: 0, count: 5 },
            { scoreT1: 4, scoreT2: 4, count: 1 },
          ],
          totalPicks: 6,
          majority: false,
          rebel: true,
        },
      ],
    });
    expect(screen.getByText("Rebelde")).toBeInTheDocument();
    expect(screen.getByText("1–0")).toBeInTheDocument();
  });
});

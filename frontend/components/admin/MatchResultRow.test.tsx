import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MatchResultRow } from "./MatchResultRow";
import type { AdminMatchRow } from "@/lib/api/admin";

const saveResultAction = vi.hoisted(() => vi.fn());
vi.mock("@/app/admin/results/actions", () => ({ saveResultAction }));

const messages = {
  admin: {
    save: "Save",
    saving: "Saving…",
    playedBadge: "Played",
    modifiedBadge: "Modified",
    errorSaving: "Save failed",
    scorePlaceholderT1: "T1",
    scorePlaceholderT2: "T2",
    advancedLabel: "Who advanced?",
  },
};

function baseMatch(over: Partial<AdminMatchRow> = {}): AdminMatchRow {
  return {
    matchId: 537415,
    roundCode: "R32",
    roundName: "Dieciseisavos",
    roundSequence: 2,
    groupCode: null,
    kickoffAt: "2026-06-29T20:30:00Z",
    team1: { id: 759, code: "GER", name: "Germany", flag: "🇩🇪" },
    team2: { id: 761, code: "PAR", name: "Paraguay", flag: "🇵🇾" },
    scoreT1: null,
    scoreT2: null,
    winnerId: null,
    advancedTeamId: null,
    played: false,
    ...over,
  };
}

function renderRow(match: AdminMatchRow) {
  render(
    <NextIntlClientProvider locale="en" messages={messages}>
      <MatchResultRow match={match} timeZone="UTC" />
    </NextIntlClientProvider>,
  );
}

afterEach(() => {
  saveResultAction.mockReset();
});

describe("MatchResultRow advancing-team picker", () => {
  it("shows the picker on a knockout draw and saves the selected advancing team", async () => {
    saveResultAction.mockResolvedValue({
      matchId: 537415,
      scoreT1: 1,
      scoreT2: 1,
      winnerId: null,
      played: true,
    });
    const user = userEvent.setup();
    renderRow(baseMatch());

    await user.type(screen.getByLabelText(/GER score/i), "1");
    await user.type(screen.getByLabelText(/PAR score/i), "1");

    // Picker appears once the knockout score is a draw.
    expect(screen.getByText(/who advanced/i)).toBeInTheDocument();
    const paraguayPick = screen.getByRole("button", { name: /paraguay advanced/i });
    await user.click(paraguayPick);

    await user.click(screen.getByRole("button", { name: /^save$/i }));

    await waitFor(() => expect(saveResultAction).toHaveBeenCalledTimes(1));
    expect(saveResultAction).toHaveBeenCalledWith(537415, 1, 1, 761);
  });

  it("blocks saving a knockout draw until an advancing team is chosen", async () => {
    const user = userEvent.setup();
    renderRow(baseMatch());

    await user.type(screen.getByLabelText(/GER score/i), "2");
    await user.type(screen.getByLabelText(/PAR score/i), "2");

    expect(screen.getByRole("button", { name: /^save$/i })).toBeDisabled();
  });

  it("does not show the picker for a group-stage draw", async () => {
    const user = userEvent.setup();
    renderRow(baseMatch({ roundCode: "GROUP", groupCode: "A" }));

    await user.type(screen.getByLabelText(/GER score/i), "1");
    await user.type(screen.getByLabelText(/PAR score/i), "1");

    expect(screen.queryByText(/who advanced/i)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^save$/i })).toBeEnabled();
  });

  it("does not show the picker for a decisive knockout score", async () => {
    const user = userEvent.setup();
    renderRow(baseMatch());

    await user.type(screen.getByLabelText(/GER score/i), "2");
    await user.type(screen.getByLabelText(/PAR score/i), "1");

    expect(screen.queryByText(/who advanced/i)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^save$/i })).toBeEnabled();
  });
});

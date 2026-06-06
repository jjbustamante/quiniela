import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { MatchView } from "@/lib/api/bracket";
import { MatchRow } from "./MatchRow";

function match(overrides: Partial<MatchView> = {}): MatchView {
  const base: MatchView = {
    id: 1,
    team1Id: 10,
    team1Name: "Brasil",
    team1Flag: "🇧🇷",
    team2Id: 20,
    team2Name: "Croacia",
    team2Flag: "🇭🇷",
    betScoreT1: null,
    betScoreT2: null,
  } as MatchView;
  return { ...base, ...overrides };
}

const baseProps = {
  onTapScore: () => {},
  onAskPaul: () => {},
  paulLabelEmpty: "PAUL DECIDE",
  paulLabelFilled: "CAMBIAR · PAUL",
};

describe("MatchRow", () => {
  it("disables the Paul action while pending", () => {
    render(<MatchRow match={match()} {...baseProps} paulPending />);
    const paulButton = screen.getByRole("button", { name: /paul decide/i });
    expect(paulButton).toBeDisabled();
  });

  it("renders a feedback node when provided", () => {
    render(
      <MatchRow
        match={match()}
        {...baseProps}
        feedback={<div data-testid="paul-feedback">panel</div>}
      />,
    );
    expect(screen.getByTestId("paul-feedback")).toBeInTheDocument();
  });

  it("renders no feedback node by default", () => {
    render(<MatchRow match={match()} {...baseProps} />);
    expect(screen.queryByTestId("paul-feedback")).not.toBeInTheDocument();
  });
});

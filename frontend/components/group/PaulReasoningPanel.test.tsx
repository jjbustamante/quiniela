import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { PaulReasoningPanel } from "./PaulReasoningPanel";

describe("PaulReasoningPanel", () => {
  it("renders the header and reasoning", () => {
    render(
      <PaulReasoningPanel
        header="🐙 PAUL DICE · 2–1"
        reasoning="Brasil llega con toda su ofensiva"
        dismissLabel="OCULTAR"
        onDismiss={() => {}}
      />,
    );
    expect(screen.getByText(/paul dice/i)).toBeInTheDocument();
    expect(screen.getByText(/brasil llega/i)).toBeInTheDocument();
  });

  it("omits the reasoning paragraph when reasoning is empty", () => {
    render(
      <PaulReasoningPanel
        header="🐙 PAUL ESTÁ DE ACUERDO CONTIGO"
        reasoning=""
        dismissLabel="OCULTAR"
        onDismiss={() => {}}
      />,
    );
    expect(screen.getByText(/de acuerdo/i)).toBeInTheDocument();
    expect(screen.queryByRole("paragraph")).not.toBeInTheDocument();
  });

  it("calls onDismiss when the dismiss control is clicked", async () => {
    const onDismiss = vi.fn();
    render(
      <PaulReasoningPanel header="x" dismissLabel="OCULTAR" onDismiss={onDismiss} />,
    );
    await userEvent.click(screen.getByRole("button", { name: /ocultar/i }));
    expect(onDismiss).toHaveBeenCalledOnce();
  });
});

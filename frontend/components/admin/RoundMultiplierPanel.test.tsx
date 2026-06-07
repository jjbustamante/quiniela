import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import { RoundMultiplierPanel } from "./RoundMultiplierPanel";

const messages = {
  roundMultipliers: {
    title: "Per-round multipliers",
    intro: "intro",
    lockWarning: "Lock before knockouts",
    multiplierLabel: "Multiplier",
    save: "Save multipliers",
    saved: "Saved",
    rangeError: "Each multiplier must be between 1 and 10.",
  },
};

const rounds = [
  { code: "R32", name: "Dieciseisavos", multiplier: 2 },
  { code: "FINAL", name: "Final", multiplier: 2 },
];

function renderPanel(saveAction = vi.fn().mockResolvedValue({ ok: true })) {
  render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <RoundMultiplierPanel rounds={rounds} saveAction={saveAction} />
    </NextIntlClientProvider>,
  );
  return saveAction;
}

describe("RoundMultiplierPanel", () => {
  it("renders an input per round and the lock warning", () => {
    renderPanel();
    expect(screen.getByText(/lock before knockouts/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/dieciseisavos/i)).toHaveValue(2);
    expect(screen.getByLabelText(/final/i)).toHaveValue(2);
  });

  it("saves the edited multipliers by code", async () => {
    const save = renderPanel();
    const final = screen.getByLabelText(/final/i);
    await userEvent.clear(final);
    await userEvent.type(final, "5");
    await userEvent.click(screen.getByRole("button", { name: /save multipliers/i }));
    expect(save).toHaveBeenCalledWith({
      rounds: [
        { code: "R32", multiplier: 2 },
        { code: "FINAL", multiplier: 5 },
      ],
    });
  });

  it("disables save when a value is out of range", async () => {
    renderPanel();
    const r32 = screen.getByLabelText(/dieciseisavos/i);
    await userEvent.clear(r32);
    await userEvent.type(r32, "0");
    expect(screen.getByRole("button", { name: /save multipliers/i })).toBeDisabled();
  });
});

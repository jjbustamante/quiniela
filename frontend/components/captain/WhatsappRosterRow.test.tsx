import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import { WhatsappRosterRow } from "./WhatsappRosterRow";
import type { RosterEntry } from "@/lib/api/captain-whatsapp";
import type { ToggleResult } from "@/app/captain/whatsapp/actions";

const messages = {
  captainWhatsapp: {
    visibleOn: "Visible",
    visibleOff: "Hidden",
  },
};

function renderRow(
  entry: RosterEntry,
  action = vi.fn().mockResolvedValue({ ok: true } as ToggleResult),
) {
  render(
    <NextIntlClientProvider locale="en" messages={messages}>
      <WhatsappRosterRow entry={entry} setVisibilityAction={action} />
    </NextIntlClientProvider>,
  );
  return action;
}

const offEntry: RosterEntry = { userId: 1, displayName: "Ana García", visible: false };
const onEntry: RosterEntry = { userId: 2, displayName: "Luis Pérez", visible: true };

describe("WhatsappRosterRow", () => {
  it("renders the player display name", () => {
    renderRow(offEntry);
    expect(screen.getByText("Ana García")).toBeInTheDocument();
  });

  it("shows visibleOff label when entry is not visible", () => {
    renderRow(offEntry);
    expect(screen.getByRole("button", { name: /hidden/i })).toBeInTheDocument();
  });

  it("shows visibleOn label when entry is visible", () => {
    renderRow(onEntry);
    expect(screen.getByRole("button", { name: /visible/i })).toBeInTheDocument();
  });

  it("calls action with {userId, visible:true} when off-row button is clicked", async () => {
    const action = renderRow(offEntry);
    await userEvent.click(screen.getByRole("button", { name: /hidden/i }));
    expect(action).toHaveBeenCalledWith({ userId: 1, visible: true });
  });

  it("calls action with {userId, visible:false} when on-row button is clicked", async () => {
    const action = renderRow(onEntry);
    await userEvent.click(screen.getByRole("button", { name: /visible/i }));
    expect(action).toHaveBeenCalledWith({ userId: 2, visible: false });
  });

  it("sets aria-pressed correctly", () => {
    renderRow(offEntry);
    expect(screen.getByRole("button", { name: /hidden/i })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });
});

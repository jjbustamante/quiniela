import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";

const paulFillAllAction = vi.fn();
vi.mock("@/app/home/actions", () => ({ paulFillAllAction: () => paulFillAllAction() }));

import { PaulFillAllButton } from "./PaulFillAllButton";

const messages = {
  lobby: {
    askPaulFillAll: "PAUL LLENA TODO",
    fillLocked: "Las apuestas están cerradas para esta ronda.",
    fillDone: "🐙 Paul rellenó {count} pronósticos. ¡Suerte!",
    fillNothing1: "🐙 Ya tenías todo listo. Paul se echó una siesta.",
    fillNothing2: "🐙 Nada que rellenar — ¡vas adelantado!",
    fillError: "Paul no pudo rellenar. Intenta de nuevo.",
  },
};

function renderButton() {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <PaulFillAllButton />
    </NextIntlClientProvider>,
  );
}

describe("PaulFillAllButton", () => {
  it("shows the count when Paul fills picks", async () => {
    paulFillAllAction.mockResolvedValueOnce({ ok: true, created: 3 });
    renderButton();
    await userEvent.click(screen.getByRole("button", { name: /paul llena todo/i }));
    expect(await screen.findByText(/rellenó 3 pronósticos/i)).toBeInTheDocument();
  });

  it("shows a funny message when nothing was filled", async () => {
    paulFillAllAction.mockResolvedValueOnce({ ok: true, created: 0 });
    renderButton();
    await userEvent.click(screen.getByRole("button", { name: /paul llena todo/i }));
    expect(await screen.findByText(/(ya tenías todo listo|nada que rellenar)/i)).toBeInTheDocument();
  });

  it("shows the locked notice on a locked round", async () => {
    paulFillAllAction.mockResolvedValueOnce({ ok: false, locked: true, error: "x" });
    renderButton();
    await userEvent.click(screen.getByRole("button", { name: /paul llena todo/i }));
    expect(await screen.findByText(/apuestas están cerradas/i)).toBeInTheDocument();
  });
});

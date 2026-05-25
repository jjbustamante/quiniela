import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi } from "vitest";
import { NumpadScoreInput } from "./NumpadScoreInput";

const messages = {
  numpad: {
    title: "Marca el resultado",
    presets: "Marcadores frecuentes",
    confirm: "Confirmar",
    cancel: "Cancelar",
  },
};

describe("NumpadScoreInput", () => {
  it("renders all digits 0-9 and confirms a single-side score", () => {
    const onConfirm = vi.fn();
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <NumpadScoreInput side="t1" onConfirm={onConfirm} onCancel={() => {}} />
      </NextIntlClientProvider>,
    );
    for (let n = 0; n <= 9; n++) {
      expect(screen.getByRole("button", { name: String(n) })).toBeInTheDocument();
    }
    fireEvent.click(screen.getByRole("button", { name: "2" }));
    fireEvent.click(screen.getByRole("button", { name: /confirmar/i }));
    expect(onConfirm).toHaveBeenCalledWith(2);
  });

  it("applies a preset score and confirms both sides", () => {
    const onConfirm = vi.fn();
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <NumpadScoreInput side="both" onConfirm={onConfirm} onCancel={() => {}} />
      </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "2-1" }));
    expect(onConfirm).toHaveBeenCalledWith({ t1: 2, t2: 1 });
  });

  it("both-mode digit entry advances active cell and confirms with both values", () => {
    const onConfirm = vi.fn();
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <NumpadScoreInput side="both" onConfirm={onConfirm} onCancel={() => {}} />
      </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "3" })); // t1 = 3, auto-advance to t2
    fireEvent.click(screen.getByRole("button", { name: "2" })); // t2 = 2
    fireEvent.click(screen.getByRole("button", { name: /confirmar/i }));
    expect(onConfirm).toHaveBeenCalledWith({ t1: 3, t2: 2 });
  });
});

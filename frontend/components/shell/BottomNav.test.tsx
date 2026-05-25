import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect } from "vitest";
import { BottomNav } from "./BottomNav";

const messages = {
  nav: {
    myQuiniela: "Mi Quiniela",
    ranking: "Tabla",
    matches: "Partidos",
    compare: "Comparar",
  },
};

describe("BottomNav", () => {
  it("renders four tabs with translated labels", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <BottomNav activeKey="myQuiniela" />
      </NextIntlClientProvider>,
    );
    expect(screen.getByRole("link", { name: /mi quiniela/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /tabla/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /partidos/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /comparar/i })).toBeInTheDocument();
  });

  it("marks the active tab with aria-current", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <BottomNav activeKey="ranking" />
      </NextIntlClientProvider>,
    );
    const active = screen.getByRole("link", { name: /tabla/i });
    expect(active).toHaveAttribute("aria-current", "page");
  });
});

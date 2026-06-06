import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { it, expect } from "vitest";
import { PhaseRail } from "./PhaseRail";
import type { PhaseChip } from "@/lib/home-phase";

const messages = { home: { chipGROUP: "Grupos", chipR32: "16vos", chipFINAL: "Final" } };
function r(ui: React.ReactNode) {
  return render(<NextIntlClientProvider locale="es-CO" messages={messages}>{ui}</NextIntlClientProvider>);
}

it("renders a chip per phase with the right link and a status glyph", () => {
  const chips: PhaseChip[] = [
    { code: "GROUP", state: "done", href: "/groups" },
    { code: "R32", state: "open", href: "/knockout/R32" },
    { code: "FINAL", state: "locked", href: "/knockout/FINAL" },
  ];
  r(<PhaseRail chips={chips} />);
  expect(screen.getByRole("link", { name: /grupos/i })).toHaveAttribute("href", "/groups");
  expect(screen.getByRole("link", { name: /16vos/i })).toHaveAttribute("href", "/knockout/R32");
  expect(screen.getByRole("link", { name: /final/i })).toHaveAttribute("href", "/knockout/FINAL");
});

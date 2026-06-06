import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { it, expect } from "vitest";
import { FocusCard } from "./FocusCard";
import type { FocusState } from "@/lib/home-phase";

const messages = {
  home: {
    fillGroupKicker: "Cierra en {when}", fillGroupHeadline: "Llena tus grupos", fillSub: "{filled}/{total} listos",
    fillGroupCta: "Seguir llenando →", fillGroupCtaEmpty: "Empezar →",
    fillKnockoutKicker: "Ronda abierta · cierra en {when}", fillKnockoutHeadline: "{round} · llena tu llave", fillKnockoutCta: "Llenar {round} →",
    liveHeadline: "En juego", livePhaseGroup: "Fase de grupos", livePhaseKnockout: "Eliminatorias", liveSub: "Tu puesto #{rank} · {points} pts", liveSubNoScore: "Aún sin puntos",
    championKicker: "Torneo terminado", championHeadline: "{rank}º puesto", championSub: "{points} pts", championSubPayout: "{points} pts · ganaste {amount}",
  },
};
function r(ui: React.ReactNode) {
  return render(<NextIntlClientProvider locale="es-CO" messages={messages}>{ui}</NextIntlClientProvider>);
}

it("fillGroup shows the headline + a CTA to /groups", () => {
  const f: FocusState = { kind: "fillGroup", filled: 4, total: 6, full: false, deadline: "2026-06-11T17:00:00Z", href: "/groups" };
  r(<FocusCard focus={f} timeZone="America/Bogota" />);
  expect(screen.getByText("Llena tus grupos")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: /llenando/i })).toHaveAttribute("href", "/groups");
});

it("fillKnockout interpolates the round name into headline and CTA", () => {
  const f: FocusState = { kind: "fillKnockout", roundCode: "R32", roundName: "Dieciseisavos", filled: 3, total: 16, full: false, deadline: "2026-06-28T17:00:00Z", href: "/knockout/R32" };
  r(<FocusCard focus={f} timeZone="America/Bogota" />);
  expect(screen.getByText(/Dieciseisavos · llena tu llave/)).toBeInTheDocument();
  expect(screen.getByRole("link", { name: /llenar dieciseisavos/i })).toHaveAttribute("href", "/knockout/R32");
});

it("champion shows place + payout and has no CTA", () => {
  const f: FocusState = { kind: "champion", rank: 2, points: 96, payoutCents: 3900 };
  r(<FocusCard focus={f} timeZone="America/Bogota" />);
  expect(screen.getByText("2º puesto")).toBeInTheDocument();
  expect(screen.queryByRole("link")).not.toBeInTheDocument();
});

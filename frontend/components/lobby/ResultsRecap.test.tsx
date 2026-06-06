import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { it, expect } from "vitest";
import { ResultsRecap } from "./ResultsRecap";
import type { Recap } from "@/lib/home-phase";

const messages = { home: { resultadosProximos: "Resultados & próximos", verPartidos: "Ver partidos →", tagFinal: "Final", preKickoffPot: "Pozo {amount} · {panas} panas", preKickoffCountdown: "Faltan {days} días para el pitazo" } };
function r(ui: React.ReactNode) {
  return render(<NextIntlClientProvider locale="es-CO" messages={messages}>{ui}</NextIntlClientProvider>);
}

it("renders recent finals with points earned and the next fixture", () => {
  const recap: Recap = {
    kind: "results",
    recent: [{ matchId: 1, t1Code: "ARG", t1Flag: "🇦🇷", t2Code: "ITA", t2Flag: "🇮🇹", s1: 3, s2: 1, pointsEarned: 10 }],
    next: { t1Code: "FRA", t1Flag: "🇫🇷", t2Code: "JPN", t2Flag: "🇯🇵", kickoffAt: "2026-06-20T20:00:00Z" },
  };
  r(<ResultsRecap recap={recap} timeZone="America/Bogota" />);
  expect(screen.getByText(/ARG/)).toBeInTheDocument();
  expect(screen.getByText(/\+10/)).toBeInTheDocument();
  expect(screen.getByText(/FRA/)).toBeInTheDocument();
});

it("renders pot + countdown before any match is played", () => {
  const recap: Recap = { kind: "preKickoff", potCents: 26000, currency: "USD", panaCount: 13, startDate: "2026-06-11" };
  r(<ResultsRecap recap={recap} timeZone="America/Bogota" />);
  expect(screen.getByText(/Pozo/)).toBeInTheDocument();
  expect(screen.getByText(/13 panas/)).toBeInTheDocument();
});

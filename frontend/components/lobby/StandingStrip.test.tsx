import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect } from "vitest";
import { StandingStrip } from "./StandingStrip";

const messages = { home: { tuPuesto: "Tu puesto", verTabla: "Ver tabla →", standingNoScore: "Aún sin puntos" } };
function r(ui: React.ReactNode) {
  return render(<NextIntlClientProvider locale="es-CO" messages={messages}>{ui}</NextIntlClientProvider>);
}

it("shows rank, points and a medal once someone has scored", () => {
  r(<StandingStrip standing={{ rank: 2, points: 88, hasScored: true }} />);
  expect(screen.getByText(/#2/)).toBeInTheDocument();
  expect(screen.getByText(/88/)).toBeInTheDocument();
  expect(screen.getByText("🥈", { exact: false })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: /ver tabla/i })).toHaveAttribute("href", "/ranking");
});

it("shows no medal before anyone has scored", () => {
  r(<StandingStrip standing={{ rank: 1, points: 0, hasScored: false }} />);
  expect(screen.queryByText("🥇", { exact: false })).not.toBeInTheDocument();
});

it("renders nothing when there is no standing", () => {
  const { container } = r(<StandingStrip standing={null} />);
  expect(container).toBeEmptyDOMElement();
});

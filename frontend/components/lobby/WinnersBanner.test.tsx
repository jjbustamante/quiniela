import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { it, expect } from "vitest";
import { WinnersBanner } from "./WinnersBanner";
import type { Winners } from "@/lib/home-phase";

const messages = {
  home: {
    winnersOverallBotHeadline: "🐙 {name} se llevó el título",
    winnersOverallBotSub: "{points} pts · les ganó a todos los panas 😅",
    winnersOverallHumanHeadline: "🏆 {name} es el campeón",
    winnersOverallHumanSub: "{points} pts en la tabla general",
    winnersPodiumTitle: "Ganadores del premio",
    winnersPodiumRowSingle: "{medal} {name} — {points} pts · {amount}",
    winnersPodiumRowTied: "{medal} {names} — {points} pts · {amount} c/u",
  },
};
function r(ui: React.ReactNode) {
  return render(<NextIntlClientProvider locale="es-CO" messages={messages}>{ui}</NextIntlClientProvider>);
}

const winners: Winners = {
  overall: { displayName: "Pulpo Paul 🐙", points: 664, isBot: true },
  prizeTop: [
    { rank: 1, payoutCentsEach: 32000, winners: [{ userId: 1, displayName: "José Manuel", points: 627, isYou: false }] },
    { rank: 2, payoutCentsEach: 6000, winners: [{ userId: 2, displayName: "Arturo", points: 620, isYou: false }] },
    {
      rank: 3,
      payoutCentsEach: 1000,
      winners: [
        { userId: 3, displayName: "Yeison", points: 613, isYou: false },
        { userId: 4, displayName: "Ricardo", points: 613, isYou: false },
      ],
    },
  ],
};

it("renders nothing when there are no winners yet", () => {
  const { container } = r(<WinnersBanner winners={null} />);
  expect(container).toBeEmptyDOMElement();
});

it("names the bot as the overall leaderboard topper", () => {
  r(<WinnersBanner winners={winners} />);
  expect(screen.getByText(/Pulpo Paul 🐙 se llevó el título/)).toBeInTheDocument();
});

it("uses the human-champion headline when the overall topper isn't a bot", () => {
  r(<WinnersBanner winners={{ ...winners, overall: { displayName: "María", points: 700, isBot: false } }} />);
  expect(screen.getByText(/María es el campeón/)).toBeInTheDocument();
});

it("renders the prize podium with a single winner per row for ranks 1 and 2", () => {
  r(<WinnersBanner winners={winners} />);
  expect(screen.getByText(/🥇 José Manuel — 627 pts · \$320/)).toBeInTheDocument();
  expect(screen.getByText(/🥈 Arturo — 620 pts · \$60/)).toBeInTheDocument();
});

it("renders both names and the per-person amount for a tied rank", () => {
  r(<WinnersBanner winners={winners} />);
  expect(screen.getByText(/🥉 Yeison & Ricardo — 613 pts · \$10 c\/u/)).toBeInTheDocument();
});

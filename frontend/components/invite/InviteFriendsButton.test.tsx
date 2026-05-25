import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect } from "vitest";
import { InviteFriendsButton } from "./InviteFriendsButton";

const messages = { lobby: { inviteFriends: "Invitar amigos" } };

describe("InviteFriendsButton", () => {
  it("renders for admin", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <InviteFriendsButton role="ADMIN" invitePath="juan-abc123" />
      </NextIntlClientProvider>,
    );
    expect(screen.getByRole("button", { name: /invitar amigos/i })).toBeInTheDocument();
  });

  it("renders for captain", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <InviteFriendsButton role="CAPTAIN" invitePath="cap-q1w2e3" />
      </NextIntlClientProvider>,
    );
    expect(screen.getByRole("button", { name: /invitar amigos/i })).toBeInTheDocument();
  });

  it("renders nothing for player", () => {
    const { container } = render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <InviteFriendsButton role="PLAYER" invitePath={null} />
      </NextIntlClientProvider>,
    );
    expect(container).toBeEmptyDOMElement();
  });
});

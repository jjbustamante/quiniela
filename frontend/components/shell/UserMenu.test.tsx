import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi } from "vitest";
import { UserMenu } from "./UserMenu";

vi.mock("@/app/auth-actions", () => ({
  signOutAction: vi.fn(),
}));

const messages = {
  common: {
    signOut: "Cerrar sesión",
  },
};

describe("UserMenu", () => {
  it("shows the user's initial in the avatar button", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <UserMenu displayName="Juan Bustamante" role="ADMIN" />
      </NextIntlClientProvider>,
    );
    expect(screen.getByRole("button", { name: /Juan Bustamante/i })).toHaveTextContent("J");
  });

  it("opens dropdown on click and shows full name + role + sign-out button", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <UserMenu displayName="Juan Bustamante" role="CAPTAIN" />
      </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: /Juan Bustamante/i }));
    expect(screen.getByText("Juan Bustamante")).toBeInTheDocument();
    expect(screen.getByText(/captain/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /cerrar sesión/i })).toBeInTheDocument();
  });

  it("toggles closed when avatar is clicked again", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <UserMenu displayName="Juan" role="PLAYER" />
      </NextIntlClientProvider>,
    );
    const avatar = screen.getByRole("button", { name: /Juan/i });
    fireEvent.click(avatar);
    expect(screen.getByRole("button", { name: /cerrar sesión/i })).toBeInTheDocument();
    fireEvent.click(avatar);
    expect(screen.queryByRole("button", { name: /cerrar sesión/i })).not.toBeInTheDocument();
  });
});

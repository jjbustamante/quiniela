import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { InviteFriendsSheet } from "./InviteFriendsSheet";

const messages = {
  invite_sheet: {
    title: "Invita a un amigo",
    linkLabel: "Tu enlace personal",
    copy: "Copiar",
    copied: "Copiado",
    whatsapp: "Compartir por WhatsApp",
    whatsappMessage: "Únete: {url}",
  },
};

describe("InviteFriendsSheet", () => {
  beforeEach(() => {
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
  });

  it("renders the personal link and WhatsApp share link", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <InviteFriendsSheet invitePath="juan-abc123" onClose={() => {}} />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText(/juan-abc123/)).toBeInTheDocument();
    const wa = screen.getByRole("link", { name: /WhatsApp/i });
    expect(wa.getAttribute("href")).toContain("wa.me");
    expect(wa.getAttribute("href")).toContain(encodeURIComponent("juan-abc123"));
  });

  it("copies the link on click", async () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <InviteFriendsSheet invitePath="juan-abc123" onClose={() => {}} />
      </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: /copiar/i }));
    expect(navigator.clipboard.writeText).toHaveBeenCalled();
  });
});

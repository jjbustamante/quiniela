import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { it, expect, describe } from "vitest";
import { WhatsappGroupCard } from "./WhatsappGroupCard";

const messages = {
  lobby: {
    whatsappGroupTitle: "JOIN THE GROUP",
    whatsappGroupSubtitle: "Chat with everyone on WhatsApp",
    whatsappGroupManage: "Manage who sees it",
  },
};

function r(ui: React.ReactNode) {
  return render(
    <NextIntlClientProvider locale="en" messages={messages}>
      {ui}
    </NextIntlClientProvider>
  );
}

const TEST_URL = "https://chat.whatsapp.com/abc123";

describe("WhatsappGroupCard", () => {
  it("renders a link with the group url, target=_blank, rel=noopener noreferrer", () => {
    r(<WhatsappGroupCard url={TEST_URL} canManage={false} />);
    const link = screen.getByRole("link", { name: /join the group/i });
    expect(link).toHaveAttribute("href", TEST_URL);
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("does NOT show the manage link when canManage is false", () => {
    r(<WhatsappGroupCard url={TEST_URL} canManage={false} />);
    expect(screen.queryByRole("link", { name: /manage who sees it/i })).not.toBeInTheDocument();
  });

  it("shows the manage link pointing at /captain/whatsapp when canManage is true", () => {
    r(<WhatsappGroupCard url={TEST_URL} canManage={true} />);
    const manageLink = screen.getByRole("link", { name: /manage who sees it/i });
    expect(manageLink).toHaveAttribute("href", "/captain/whatsapp");
  });
});

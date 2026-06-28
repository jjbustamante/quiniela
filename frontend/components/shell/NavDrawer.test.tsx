import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi } from "vitest";
import { NavDrawer } from "./NavDrawer";

vi.mock("@/app/auth-actions", () => ({
  signOutAction: vi.fn(),
}));

const messages = {
  common: { signOut: "Cerrar sesión" },
  nav: {
    menu: "Menú",
    home: "Inicio",
    payments: "Pagos",
    results: "Resultados",
    config: "Configuración",
    whatsappGroup: "Grupo WhatsApp",
    paulAdmin: "Pulpo Paul",
  },
};

function renderDrawer(role: "ADMIN" | "CAPTAIN" | "PLAYER", showWhatsappGroup = false) {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <NavDrawer role={role} showWhatsappGroup={showWhatsappGroup} />
    </NextIntlClientProvider>,
  );
}

describe("NavDrawer", () => {
  it("is closed initially — no panel items shown", () => {
    renderDrawer("ADMIN");
    expect(screen.queryByRole("link", { name: /inicio/i })).not.toBeInTheDocument();
  });

  it("opens on trigger click and shows admin items", () => {
    renderDrawer("ADMIN");
    fireEvent.click(screen.getByRole("button", { name: /menú/i }));
    expect(screen.getByRole("link", { name: /inicio/i })).toHaveAttribute("href", "/home");
    expect(screen.getByRole("link", { name: /pagos/i })).toHaveAttribute("href", "/admin/payments");
    expect(screen.getByRole("link", { name: /resultados/i })).toHaveAttribute("href", "/admin/results");
    expect(screen.getByRole("link", { name: /configuración/i })).toHaveAttribute("href", "/admin/config");
    expect(screen.getByRole("link", { name: /pulpo paul/i })).toHaveAttribute("href", "/admin/paul");
    expect(screen.getByRole("button", { name: /cerrar sesión/i })).toBeInTheDocument();
    // The admin doesn't invite players, so the per-player WhatsApp roster is
    // not theirs to manage — they set the link in /admin/config instead.
    expect(screen.queryByRole("link", { name: /grupo whatsapp/i })).not.toBeInTheDocument();
  });

  it("routes captain Pagos to the captain page, hides Resultados, shows WhatsApp roster when enabled", () => {
    renderDrawer("CAPTAIN", true);
    fireEvent.click(screen.getByRole("button", { name: /menú/i }));
    expect(screen.getByRole("link", { name: /pagos/i })).toHaveAttribute("href", "/captain/payments");
    expect(screen.queryByRole("link", { name: /resultados/i })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /grupo whatsapp/i })).toHaveAttribute(
      "href",
      "/captain/whatsapp",
    );
  });

  it("hides the WhatsApp roster link for a captain when the admin has it disabled", () => {
    renderDrawer("CAPTAIN", false);
    fireEvent.click(screen.getByRole("button", { name: /menú/i }));
    expect(screen.queryByRole("link", { name: /grupo whatsapp/i })).not.toBeInTheDocument();
  });

  it("shows only Inicio + sign-out for a player (no Pagos/Resultados)", () => {
    renderDrawer("PLAYER");
    fireEvent.click(screen.getByRole("button", { name: /menú/i }));
    expect(screen.getByRole("link", { name: /inicio/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /pagos/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /resultados/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /cerrar sesión/i })).toBeInTheDocument();
  });

  it("closes when the trigger is clicked again", () => {
    renderDrawer("ADMIN");
    const trigger = screen.getByRole("button", { name: /menú/i });
    fireEvent.click(trigger);
    expect(screen.getByRole("link", { name: /inicio/i })).toBeInTheDocument();
    fireEvent.click(trigger);
    expect(screen.queryByRole("link", { name: /inicio/i })).not.toBeInTheDocument();
  });

  it("closes on Escape", () => {
    renderDrawer("ADMIN");
    fireEvent.click(screen.getByRole("button", { name: /menú/i }));
    expect(screen.getByRole("link", { name: /inicio/i })).toBeInTheDocument();
    fireEvent.keyDown(window, { key: "Escape" });
    expect(screen.queryByRole("link", { name: /inicio/i })).not.toBeInTheDocument();
  });

  it("closes on backdrop click", () => {
    renderDrawer("ADMIN");
    fireEvent.click(screen.getByRole("button", { name: /menú/i }));
    fireEvent.click(screen.getByTestId("nav-backdrop"));
    expect(screen.queryByRole("link", { name: /inicio/i })).not.toBeInTheDocument();
  });
});

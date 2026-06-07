import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import { CommunityConfigPanel } from "./CommunityConfigPanel";

const messages = {
  adminConfig: {
    community: "Comunidad",
    whatsappUrl: "Enlace del grupo de WhatsApp",
    whatsappUrlHelp: "Debe empezar con https://chat.whatsapp.com/",
    whatsappEnabled: "Disponible para los capitanes",
    save: "Guardar",
    saved: "Guardado",
  },
};

function renderPanel(
  config = { url: null as string | null, enabled: false },
  saveAction = vi.fn().mockResolvedValue({ ok: true }),
) {
  render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <CommunityConfigPanel config={config} saveAction={saveAction} />
    </NextIntlClientProvider>,
  );
  return saveAction;
}

describe("CommunityConfigPanel", () => {
  it("renders the url input and enabled checkbox", () => {
    renderPanel();
    expect(screen.getByLabelText(/enlace del grupo/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/disponible para los capitanes/i)).toBeInTheDocument();
  });

  it("calls saveAction with url and enabled when saved", async () => {
    const save = renderPanel();
    const urlInput = screen.getByLabelText(/enlace del grupo/i);
    await userEvent.clear(urlInput);
    await userEvent.type(urlInput, "https://chat.whatsapp.com/ABC");
    const checkbox = screen.getByLabelText(/disponible para los capitanes/i);
    await userEvent.click(checkbox);
    await userEvent.click(screen.getByRole("button", { name: /guardar/i }));
    expect(save).toHaveBeenCalledWith({
      url: "https://chat.whatsapp.com/ABC",
      enabled: true,
    });
  });
});

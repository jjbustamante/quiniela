import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import { PaulAdminPanel } from "./PaulAdminPanel";
import type { PaulJobStatus } from "@/lib/api/paul-admin";

const messages = {
  paulAdmin: {
    title: "Pulpo Paul",
    intro: "Genera y revela.",
    generate: "1. Generar candidatos",
    synthesize: "2. Sintetizar oficiales",
    reveal: "3. Revelar",
    statusLabel: "Estado",
    alreadyRunning: "Ya hay un trabajo de Paul en ejecución.",
    error: "Algo salió mal. Inténtalo de nuevo.",
    revealed:
      "{count, plural, =0 {No había apuestas nuevas que revelar} one {1 apuesta revelada} other {# apuestas reveladas}}",
    state: { IDLE: "Inactivo", RUNNING: "En ejecución", DONE: "Completado", FAILED: "Falló" },
  },
};

const IDLE: PaulJobStatus = {
  phase: null,
  state: "IDLE",
  processed: 0,
  total: 0,
  error: null,
  startedAt: null,
  finishedAt: null,
};

const RUNNING: PaulJobStatus = {
  phase: "generate",
  state: "RUNNING",
  processed: 0,
  total: 144,
  error: null,
  startedAt: "2026-06-28T13:00:00Z",
  finishedAt: null,
};

function renderPanel(overrides: Partial<React.ComponentProps<typeof PaulAdminPanel>> = {}) {
  const props = {
    initialStatus: IDLE,
    generateAction: vi.fn().mockResolvedValue({ ok: true, status: RUNNING }),
    synthesizeAction: vi.fn().mockResolvedValue({ ok: true, status: RUNNING }),
    revealAction: vi.fn().mockResolvedValue({ ok: true, result: { betsCreated: 3 } }),
    statusAction: vi.fn().mockResolvedValue(RUNNING),
    ...overrides,
  };
  render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <PaulAdminPanel {...props} />
    </NextIntlClientProvider>,
  );
  return props;
}

describe("PaulAdminPanel", () => {
  it("renders the three job buttons and the idle status", () => {
    renderPanel();
    expect(screen.getByRole("button", { name: /generar candidatos/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sintetizar oficiales/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /revelar/i })).toBeInTheDocument();
    expect(screen.getByText(/inactivo/i)).toBeInTheDocument();
  });

  it("starts generation and shows running progress with buttons disabled", async () => {
    const props = renderPanel();
    await userEvent.click(screen.getByRole("button", { name: /generar candidatos/i }));

    expect(props.generateAction).toHaveBeenCalledOnce();
    await waitFor(() => expect(screen.getByText(/en ejecución/i)).toBeInTheDocument());
    // Progress region appears (processed / total) and the buttons lock while running.
    expect(screen.getByRole("status")).toHaveTextContent("0 / 144");
    expect(screen.getByRole("button", { name: /generar candidatos/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /revelar/i })).toBeDisabled();
  });

  it("reveals and reports how many bets were created", async () => {
    const props = renderPanel();
    await userEvent.click(screen.getByRole("button", { name: /revelar/i }));

    expect(props.revealAction).toHaveBeenCalledOnce();
    await waitFor(() =>
      expect(screen.getByText(/3 apuestas reveladas/i)).toBeInTheDocument(),
    );
  });

  it("surfaces the already-running conflict message", async () => {
    renderPanel({ generateAction: vi.fn().mockResolvedValue({ ok: false, conflict: true }) });
    await userEvent.click(screen.getByRole("button", { name: /generar candidatos/i }));
    await waitFor(() =>
      expect(screen.getByText(/ya hay un trabajo de paul en ejecución/i)).toBeInTheDocument(),
    );
  });
});

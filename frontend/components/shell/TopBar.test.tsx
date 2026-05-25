import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi } from "vitest";
import { TopBar } from "./TopBar";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: vi.fn() }),
  usePathname: () => "/",
}));

const messages = { common: { appName: "Quiniela Panas" } };

describe("TopBar", () => {
  it("renders default title from i18n when none provided", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <TopBar />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText("Quiniela Panas")).toBeInTheDocument();
  });

  it("renders custom title + meta", () => {
    render(
      <NextIntlClientProvider locale="es-CO" messages={messages}>
        <TopBar title="Tabla" meta="JOR 14" />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText("Tabla")).toBeInTheDocument();
    expect(screen.getByText("JOR 14")).toBeInTheDocument();
  });
});

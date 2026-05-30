import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi } from "vitest";
import { CompareModeToggle } from "./CompareModeToggle";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams("mode=group"),
}));

const messages = { compare: { modeH2H: "1 vs 1", modeGroup: "Grupo" } };

function renderToggle(mode: "group" | "h2h") {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <CompareModeToggle mode={mode} />
    </NextIntlClientProvider>,
  );
}

describe("CompareModeToggle", () => {
  it("navigates to h2h mode when the 1 vs 1 tab is clicked", () => {
    renderToggle("group");
    fireEvent.click(screen.getByText("1 vs 1"));
    expect(push).toHaveBeenCalledWith(expect.stringContaining("mode=h2h"));
  });
});

import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StageSection } from "./StageSection";

describe("StageSection", () => {
  it("renders the header + count", () => {
    render(
      <StageSection header="16vos" count={4}>
        <div>child</div>
      </StageSection>,
    );
    expect(screen.getByTestId("stage-header")).toHaveTextContent("16vos");
    expect(screen.getByText("4")).toBeInTheDocument();
    expect(screen.getByText("child")).toBeInTheDocument();
  });

  it("is open when defaultOpen and closed otherwise", () => {
    const { container, rerender } = render(
      <StageSection header="A" count={1} defaultOpen>
        <div>x</div>
      </StageSection>,
    );
    expect(container.querySelector("details")).toHaveAttribute("open");

    rerender(
      <StageSection header="A" count={1} defaultOpen={false}>
        <div>x</div>
      </StageSection>,
    );
    expect(container.querySelector("details")).not.toHaveAttribute("open");
  });
});

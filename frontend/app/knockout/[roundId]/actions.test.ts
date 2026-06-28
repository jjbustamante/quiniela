import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("@/lib/api/bracket", () => ({ saveBet: vi.fn().mockResolvedValue(undefined) }));
vi.mock("@/lib/api/paul", () => ({ suggestForMatch: vi.fn() }));
vi.mock("@/lib/api/ignore-locked", () => ({ ignoreLockedRace: vi.fn() }));
vi.mock("@/lib/api/client", () => ({
  ApiError: class ApiError extends Error {
    constructor(public status: number, message: string) { super(message); }
  },
}));

import { acceptPaulSuggestionAction } from "./actions";
import { saveBet } from "@/lib/api/bracket";
import { suggestForMatch } from "@/lib/api/paul";

describe("acceptPaulSuggestionAction", () => {
  beforeEach(() => vi.clearAllMocks());

  it("forwards Paul's predictedWinnerId to saveBet on a knockout draw", async () => {
    vi.mocked(suggestForMatch).mockResolvedValue({
      scoreT1: 1,
      scoreT2: 1,
      reasoning: "empate, avanza local",
      predictedWinnerId: 7,
    });

    const out = await acceptPaulSuggestionAction(123, "R32");

    expect(out).toEqual({ ok: true, scoreT1: 1, scoreT2: 1, reasoning: "empate, avanza local" });
    expect(saveBet).toHaveBeenCalledWith(123, 1, 1, 7);
  });
});

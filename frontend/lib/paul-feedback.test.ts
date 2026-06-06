import { describe, expect, it } from "vitest";
import {
  singleMatchFeedback,
  fillAllFeedback,
  pickKey,
  KEPT_HEADER_KEYS,
  FILL_NOTHING_KEYS,
} from "./paul-feedback";

describe("singleMatchFeedback", () => {
  it("is 'changed' when Paul's score differs from the prior pick", () => {
    const fb = singleMatchFeedback(
      { t1: 1, t2: 0 },
      { ok: true, scoreT1: 2, scoreT2: 1, reasoning: "Brasil llega fuerte" },
    );
    expect(fb).toEqual({ kind: "changed", scoreT1: 2, scoreT2: 1, reasoning: "Brasil llega fuerte" });
  });

  it("is 'changed' when the prior pick was empty", () => {
    const fb = singleMatchFeedback(
      { t1: null, t2: null },
      { ok: true, scoreT1: 2, scoreT2: 1, reasoning: "x" },
    );
    expect(fb).toEqual({ kind: "changed", scoreT1: 2, scoreT2: 1, reasoning: "x" });
  });

  it("is 'kept' when Paul agrees with the existing pick", () => {
    const fb = singleMatchFeedback(
      { t1: 2, t2: 1 },
      { ok: true, scoreT1: 2, scoreT2: 1, reasoning: "Igual que tú" },
    );
    expect(fb).toEqual({ kind: "kept", scoreT1: 2, scoreT2: 1, reasoning: "Igual que tú" });
  });

  it("is 'locked' when the round was locked", () => {
    expect(singleMatchFeedback({ t1: null, t2: null }, { ok: false, locked: true })).toEqual({
      kind: "locked",
    });
  });

  it("is 'error' on a non-locked failure", () => {
    expect(singleMatchFeedback({ t1: null, t2: null }, { ok: false, locked: false })).toEqual({
      kind: "error",
    });
  });
});

describe("fillAllFeedback", () => {
  it("is 'filled' with the count when Paul created picks", () => {
    expect(fillAllFeedback({ ok: true, created: 5 })).toEqual({ kind: "filled", count: 5 });
  });

  it("is 'nothing' when nothing was created", () => {
    expect(fillAllFeedback({ ok: true, created: 0 })).toEqual({ kind: "nothing" });
  });

  it("is 'locked' when the round was locked", () => {
    expect(fillAllFeedback({ ok: false, locked: true, error: "x" })).toEqual({ kind: "locked" });
  });

  it("is 'error' on a non-locked failure", () => {
    expect(fillAllFeedback({ ok: false, locked: false, error: "boom" })).toEqual({ kind: "error" });
  });
});

describe("pickKey", () => {
  it("returns the first key at rand 0", () => {
    expect(pickKey(["a", "b", "c"], 0)).toBe("a");
  });

  it("returns the last key as rand approaches 1", () => {
    expect(pickKey(["a", "b", "c"], 0.999)).toBe("c");
  });

  it("always returns a member of the set", () => {
    for (const r of [0, 0.25, 0.5, 0.75, 0.9999]) {
      expect(KEPT_HEADER_KEYS).toContain(pickKey(KEPT_HEADER_KEYS, r));
      expect(FILL_NOTHING_KEYS).toContain(pickKey(FILL_NOTHING_KEYS, r));
    }
  });

  it("returns the last key at rand exactly 1", () => {
    expect(pickKey(["a", "b", "c"], 1)).toBe("c");
  });
});

import { describe, it, expect } from "vitest";
import { formatMatchDateTime, formatDeadline } from "./format-datetime";

describe("format-datetime", () => {
  const iso = "2026-06-12T17:00:00Z";

  it("formats a match instant in America/Bogota (UTC-5)", () => {
    expect(formatMatchDateTime(iso, "America/Bogota")).toBe("12 JUN · 12:00");
  });

  it("formats the same instant in America/Caracas (UTC-4)", () => {
    expect(formatMatchDateTime(iso, "America/Caracas")).toBe("12 JUN · 13:00");
  });

  it("formats in UTC when asked", () => {
    expect(formatMatchDateTime(iso, "UTC")).toBe("12 JUN · 17:00");
  });

  it("formatDeadline uses the lock-badge DD.MMM HH:MM shape", () => {
    expect(formatDeadline(iso, "America/Bogota")).toBe("12.JUN 12:00");
  });

  it("returns the raw iso on an unparseable input", () => {
    expect(formatMatchDateTime("not-a-date", "America/Bogota")).toBe("not-a-date");
  });

  it("falls back gracefully on a bad zone (returns iso)", () => {
    expect(formatMatchDateTime(iso, "Mars/Phobos")).toBe(iso);
  });
});

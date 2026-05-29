const MONTH_ABBR_ES = [
  "ENE", "FEB", "MAR", "ABR", "MAY", "JUN",
  "JUL", "AGO", "SEP", "OCT", "NOV", "DIC",
];

/**
 * Extract day-of-month, 0-based month, hour and minute of an instant AS SEEN in
 * a given IANA zone, using Intl so DST and offset are correct. Returns null if
 * the instant or the zone is invalid.
 */
function partsInZone(
  iso: string,
  timeZone: string,
): { day: number; month: number; hour: string; minute: string } | null {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  try {
    const fmt = new Intl.DateTimeFormat("en-US", {
      timeZone,
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
    const parts = fmt.formatToParts(d);
    const get = (t: string) => parts.find((p) => p.type === t)?.value ?? "";
    const day = Number(get("day"));
    const month = Number(get("month")) - 1;
    let hour = get("hour");
    if (hour === "24") hour = "00"; // Intl can emit "24" at midnight in some envs
    const minute = get("minute");
    if (!day || month < 0) return null;
    return { day, month, hour, minute };
  } catch {
    return null;
  }
}

/** "12 JUN · 12:00" — match row kickoff label, rendered in the user's zone. */
export function formatMatchDateTime(iso: string, timeZone: string): string {
  const p = partsInZone(iso, timeZone);
  if (!p) return iso;
  return `${String(p.day).padStart(2, "0")} ${MONTH_ABBR_ES[p.month]} · ${p.hour}:${p.minute}`;
}

/** "12.JUN 12:00" — lock-badge / deadline label, rendered in the user's zone. */
export function formatDeadline(iso: string, timeZone: string): string {
  const p = partsInZone(iso, timeZone);
  if (!p) return iso;
  return `${String(p.day).padStart(2, "0")}.${MONTH_ABBR_ES[p.month]} ${p.hour}:${p.minute}`;
}

# Paul Feedback UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give users visible feedback when Paul fills their picks — per-match reasoning + a "kept your pick" message inline under each match row, and a result summary for "Paul fills all".

**Architecture:** Frontend-only. The backend already returns `reasoning` from `/api/paul/suggest` and `{created}` from `/api/paul/fill`; the UI currently discards both. A pure, unit-tested `lib/paul-feedback.ts` decides what to show; server actions return structured results instead of `void`; client drill-in components store per-match feedback and render a shared `PaulReasoningPanel`. The fill-all button renders a summary message.

**Tech Stack:** Next.js 16 (React 19) server components + server actions, TypeScript, Tailwind, `next-intl` (es-CO primary, en parallel), Vitest + React Testing Library.

**Working directory:** `frontend/` inside the `.worktrees/paul-feedback` worktree (branch `fix/paul-feedback`). Run all commands from `frontend/`.

---

## File Structure

**Create**
- `frontend/lib/paul-feedback.ts` — pure decision logic + message-key sets (the testable core)
- `frontend/lib/paul-feedback.test.ts` — unit tests for the above
- `frontend/components/group/PaulReasoningPanel.tsx` — presentational panel (label props, no i18n hook)
- `frontend/components/group/PaulReasoningPanel.test.tsx`
- `frontend/components/group/MatchRow.test.tsx` — new tests for the MatchRow additions

**Modify**
- `frontend/messages/es-CO.json` — add `group.*` + `lobby.*` keys
- `frontend/messages/en.json` — parallel keys
- `frontend/components/group/MatchRow.tsx` — add `paulPending` + `feedback` slot
- `frontend/app/group/[groupId]/actions.ts` — `acceptPaulSuggestionAction` returns `AcceptOutcome`
- `frontend/app/knockout/[roundId]/actions.ts` — same change
- `frontend/components/group/GroupDrillIn.tsx` — store feedback + pending, render panel
- `frontend/components/knockout/KnockoutDrillIn.tsx` — same wiring
- `frontend/components/lobby/PaulFillAllButton.tsx` — render summary message
- `frontend/components/lobby/PaulFillAllButton.test.tsx` — new tests (create if absent)

**Why a separate `paul-feedback.ts`:** server-action files (`"use server"`) and client components are both awkward to unit-test (Next runtime, `revalidatePath`, i18n hooks). Putting every branch decision in a pure module makes the logic fully testable and keeps the actions/components as thin adapters — the same split used by `lib/ranking-payouts.ts`.

---

## Task 1: Pure feedback module

**Files:**
- Create: `frontend/lib/paul-feedback.ts`
- Test: `frontend/lib/paul-feedback.test.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/lib/paul-feedback.test.ts`:

```ts
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
    expect(fb.kind).toBe("changed");
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
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm vitest run lib/paul-feedback.test.ts`
Expected: FAIL — cannot resolve `./paul-feedback` (module missing).

- [ ] **Step 3: Write minimal implementation**

Create `frontend/lib/paul-feedback.ts`:

```ts
/**
 * Pure decision logic for Paul's user-facing feedback. Server actions and
 * client components stay thin adapters; every branch is decided (and tested)
 * here — same split as lib/ranking-payouts.ts.
 *
 * The *Outcome types mirror the server-action return shapes structurally, so
 * this module never imports from a "use server" file.
 */

/** Return shape of `acceptPaulSuggestionAction`. */
export type AcceptOutcome =
  | { ok: true; scoreT1: number; scoreT2: number; reasoning: string }
  | { ok: false; locked: boolean };

/** Return shape of `paulFillAllAction` (structurally equal to its PaulFillResult). */
export type FillOutcome =
  | { ok: true; created: number }
  | { ok: false; locked: boolean; error: string };

export type SingleFeedback =
  | { kind: "changed"; scoreT1: number; scoreT2: number; reasoning: string }
  | { kind: "kept"; scoreT1: number; scoreT2: number; reasoning: string }
  | { kind: "locked" }
  | { kind: "error" };

export type FillFeedback =
  | { kind: "filled"; count: number }
  | { kind: "nothing" }
  | { kind: "locked" }
  | { kind: "error" };

export function singleMatchFeedback(
  prior: { t1: number | null; t2: number | null },
  outcome: AcceptOutcome,
): SingleFeedback {
  if (!outcome.ok) return outcome.locked ? { kind: "locked" } : { kind: "error" };
  const kept = prior.t1 === outcome.scoreT1 && prior.t2 === outcome.scoreT2;
  return {
    kind: kept ? "kept" : "changed",
    scoreT1: outcome.scoreT1,
    scoreT2: outcome.scoreT2,
    reasoning: outcome.reasoning,
  };
}

export function fillAllFeedback(outcome: FillOutcome): FillFeedback {
  if (!outcome.ok) return outcome.locked ? { kind: "locked" } : { kind: "error" };
  return outcome.created > 0 ? { kind: "filled", count: outcome.created } : { kind: "nothing" };
}

/** i18n key suffixes (relative to the `group` / `lobby` namespaces). */
export const KEPT_HEADER_KEYS = ["paulAgreed1", "paulAgreed2", "paulAgreed3"] as const;
export const FILL_NOTHING_KEYS = ["fillNothing1", "fillNothing2"] as const;

/** Deterministic given `rand` in [0,1). Callers pass Math.random(). */
export function pickKey(keys: readonly string[], rand: number): string {
  const i = Math.min(keys.length - 1, Math.max(0, Math.floor(rand * keys.length)));
  return keys[i];
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm vitest run lib/paul-feedback.test.ts`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/paul-feedback.ts frontend/lib/paul-feedback.test.ts
git commit -m "feat(paul): pure feedback-decision module"
```

---

## Task 2: i18n keys

**Files:**
- Modify: `frontend/messages/es-CO.json`
- Modify: `frontend/messages/en.json`

No test (data only); verified by a JSON-parse step and consumed by later tasks.

- [ ] **Step 1: Add keys to `es-CO.json`**

In `frontend/messages/es-CO.json`, add these entries inside the existing `"group"` object (alongside `paulDecide` / `paulChange`):

```json
    "paulSaid": "🐙 PAUL DICE · {t1}–{t2}",
    "paulAgreed1": "🐙 PAUL ESTÁ DE ACUERDO CONTIGO",
    "paulAgreed2": "🐙 PAUL NO LE MUEVE: IGUALITO",
    "paulAgreed3": "🐙 MISMO MARCADOR. PAUL APRUEBA",
    "paulDismiss": "OCULTAR",
    "paulError": "🐙 PAUL NO PUDO DECIDIR. INTENTA DE NUEVO",
    "paulLocked": "🔒 RONDA CERRADA"
```

And inside the existing `"lobby"` object (alongside `fillLocked`):

```json
    "fillDone": "🐙 Paul rellenó {count} pronósticos. ¡Suerte!",
    "fillNothing1": "🐙 Ya tenías todo listo. Paul se echó una siesta.",
    "fillNothing2": "🐙 Nada que rellenar — ¡vas adelantado!",
    "fillError": "Paul no pudo rellenar. Intenta de nuevo."
```

> Mind the commas: the key BEFORE each inserted block needs a trailing comma, and the LAST key in each object must NOT have one.

- [ ] **Step 2: Add parallel keys to `en.json`**

In `frontend/messages/en.json`, inside `"group"`:

```json
    "paulSaid": "🐙 PAUL SAYS · {t1}–{t2}",
    "paulAgreed1": "🐙 PAUL AGREES WITH YOU",
    "paulAgreed2": "🐙 PAUL WOULDN'T CHANGE A THING",
    "paulAgreed3": "🐙 SAME SCORE. PAUL APPROVES",
    "paulDismiss": "DISMISS",
    "paulError": "🐙 PAUL COULDN'T DECIDE. TRY AGAIN",
    "paulLocked": "🔒 ROUND CLOSED"
```

Inside `"lobby"`:

```json
    "fillDone": "🐙 Paul filled {count} picks. Good luck!",
    "fillNothing1": "🐙 Everything was already filled. Paul took a nap.",
    "fillNothing2": "🐙 Nothing to fill — you're ahead!",
    "fillError": "Paul couldn't fill. Try again."
```

- [ ] **Step 3: Verify both files are valid JSON and keys exist**

Run:
```bash
node -e "const e=require('./messages/es-CO.json'),n=require('./messages/en.json'); for (const f of [['es',e],['en',n]]) { const m=f[1]; if(!m.group.paulSaid||!m.group.paulAgreed3||!m.lobby.fillDone||!m.lobby.fillNothing2||!m.lobby.fillError) throw new Error(f[0]+' missing keys'); } console.log('ok');"
```
Expected: prints `ok`.

- [ ] **Step 4: Commit**

```bash
git add frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "i18n(paul): feedback + fill-result copy (es-CO + en)"
```

---

## Task 3: PaulReasoningPanel component

**Files:**
- Create: `frontend/components/group/PaulReasoningPanel.tsx`
- Test: `frontend/components/group/PaulReasoningPanel.test.tsx`

Presentational only — takes pre-resolved label strings (like `RankingRow`), so no i18n provider is needed to test it.

- [ ] **Step 1: Write the failing test**

Create `frontend/components/group/PaulReasoningPanel.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { PaulReasoningPanel } from "./PaulReasoningPanel";

describe("PaulReasoningPanel", () => {
  it("renders the header and reasoning", () => {
    render(
      <PaulReasoningPanel
        header="🐙 PAUL DICE · 2–1"
        reasoning="Brasil llega con toda su ofensiva"
        dismissLabel="OCULTAR"
        onDismiss={() => {}}
      />,
    );
    expect(screen.getByText(/paul dice/i)).toBeInTheDocument();
    expect(screen.getByText(/brasil llega/i)).toBeInTheDocument();
  });

  it("omits the reasoning paragraph when reasoning is empty", () => {
    render(
      <PaulReasoningPanel
        header="🐙 PAUL ESTÁ DE ACUERDO CONTIGO"
        reasoning=""
        dismissLabel="OCULTAR"
        onDismiss={() => {}}
      />,
    );
    expect(screen.getByText(/de acuerdo/i)).toBeInTheDocument();
    expect(screen.queryByRole("paragraph")).not.toBeInTheDocument();
  });

  it("calls onDismiss when the dismiss control is clicked", async () => {
    const onDismiss = vi.fn();
    render(
      <PaulReasoningPanel header="x" dismissLabel="OCULTAR" onDismiss={onDismiss} />,
    );
    await userEvent.click(screen.getByRole("button", { name: /ocultar/i }));
    expect(onDismiss).toHaveBeenCalledOnce();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm vitest run components/group/PaulReasoningPanel.test.tsx`
Expected: FAIL — cannot resolve `./PaulReasoningPanel`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/components/group/PaulReasoningPanel.tsx`:

```tsx
import { PaulBadge } from "@/components/PaulMascot";

/**
 * Inline panel rendered under a MatchRow after the user asks Paul. Pure
 * presentational — all copy is passed in already-localized, so it tests
 * without an i18n provider (same approach as RankingRow).
 */
export function PaulReasoningPanel({
  header,
  reasoning,
  dismissLabel,
  onDismiss,
  tone = "default",
}: {
  header: string;
  reasoning?: string;
  dismissLabel: string;
  onDismiss: () => void;
  tone?: "default" | "error";
}) {
  const hasReasoning = reasoning != null && reasoning.trim() !== "";
  return (
    <div
      className={`border-t-[1.5px] border-dashed border-[var(--color-line-ink)] px-3 py-2 ${
        tone === "error" ? "bg-[var(--color-accent-red)]/10" : "bg-[var(--color-bg-primary)]"
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <span className="inline-flex items-center gap-2 font-mono text-[10px] font-bold uppercase tracking-[0.12em] text-[var(--color-text-primary)]">
          <PaulBadge size={16} />
          {header}
        </span>
        <button
          type="button"
          onClick={onDismiss}
          className="shrink-0 font-mono text-[10px] font-bold uppercase tracking-[0.12em] text-[var(--color-text-muted)] hover:text-[var(--color-accent-red)]"
        >
          {dismissLabel}
        </button>
      </div>
      {hasReasoning && (
        <p className="mt-1.5 text-xs leading-snug text-[var(--color-text-primary)]">{reasoning}</p>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm vitest run components/group/PaulReasoningPanel.test.tsx`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/components/group/PaulReasoningPanel.tsx frontend/components/group/PaulReasoningPanel.test.tsx
git commit -m "feat(paul): PaulReasoningPanel inline component"
```

---

## Task 4: MatchRow — pending spinner + feedback slot

**Files:**
- Modify: `frontend/components/group/MatchRow.tsx`
- Test: `frontend/components/group/MatchRow.test.tsx` (create)

- [ ] **Step 1: Write the failing test**

Create `frontend/components/group/MatchRow.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { MatchView } from "@/lib/api/bracket";
import { MatchRow } from "./MatchRow";

function match(overrides: Partial<MatchView> = {}): MatchView {
  return {
    id: 1,
    team1Id: 10,
    team1Name: "Brasil",
    team1Flag: "🇧🇷",
    team2Id: 20,
    team2Name: "Croacia",
    team2Flag: "🇭🇷",
    betScoreT1: null,
    betScoreT2: null,
    ...(overrides as MatchView),
  } as MatchView;
}

const baseProps = {
  onTapScore: () => {},
  onAskPaul: () => {},
  paulLabelEmpty: "PAUL DECIDE",
  paulLabelFilled: "CAMBIAR · PAUL",
};

describe("MatchRow", () => {
  it("disables the Paul action while pending", () => {
    render(<MatchRow match={match()} {...baseProps} paulPending />);
    const paulButton = screen.getByRole("button", { name: /paul decide/i });
    expect(paulButton).toBeDisabled();
  });

  it("renders a feedback node when provided", () => {
    render(
      <MatchRow
        match={match()}
        {...baseProps}
        feedback={<div data-testid="paul-feedback">panel</div>}
      />,
    );
    expect(screen.getByTestId("paul-feedback")).toBeInTheDocument();
  });

  it("renders no feedback node by default", () => {
    render(<MatchRow match={match()} {...baseProps} />);
    expect(screen.queryByTestId("paul-feedback")).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm vitest run components/group/MatchRow.test.tsx`
Expected: FAIL — `paulPending` / `feedback` props don't exist yet, so the pending button is not disabled and the feedback node never renders (the first and second tests fail).

- [ ] **Step 3: Add the props and rendering to `MatchRow.tsx`**

In `frontend/components/group/MatchRow.tsx`, add `React` import at the top (for the `ReactNode` type) — add this as the first import line:

```tsx
import type { ReactNode } from "react";
```

Add two props to the destructured params (after `locked = false,`):

```tsx
  paulPending = false,
  feedback,
```

Add their types to the prop type block (after `locked?: boolean;`):

```tsx
  paulPending?: boolean;
  feedback?: ReactNode;
```

Change the Paul `<button>`'s `disabled` from:

```tsx
        disabled={locked}
```
to:
```tsx
        disabled={locked || paulPending}
```

Replace the trailing arrow span inside the Paul button:

```tsx
        <span className="text-[var(--color-text-primary)]">→</span>
```
with a spinner-or-arrow:
```tsx
        {paulPending ? (
          <span
            aria-label="cargando"
            className="inline-block h-3 w-3 animate-spin rounded-full border-[1.5px] border-[var(--color-text-primary)] border-t-transparent"
          />
        ) : (
          <span className="text-[var(--color-text-primary)]">→</span>
        )}
```

Finally, render the feedback node right after the Paul `</button>` and before the component's closing `</div>`:

```tsx
      {feedback}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm vitest run components/group/MatchRow.test.tsx`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/components/group/MatchRow.tsx frontend/components/group/MatchRow.test.tsx
git commit -m "feat(paul): MatchRow pending spinner + feedback slot"
```

---

## Task 5: Server actions return AcceptOutcome

**Files:**
- Modify: `frontend/app/group/[groupId]/actions.ts:22-30`
- Modify: `frontend/app/knockout/[roundId]/actions.ts:24-32`

No unit test: these call `revalidatePath` (Next runtime) and the network, and no server action in this repo is unit-tested. The decision logic they feed was tested in Task 1; behavior is verified by typecheck (Task 8) + manual run. Keep them thin.

- [ ] **Step 1: Update the group action**

In `frontend/app/group/[groupId]/actions.ts`, add a type-only import near the top (after the existing imports):

```ts
import type { AcceptOutcome } from "@/lib/paul-feedback";
import { ApiError } from "@/lib/api/client";
```

Replace the whole `acceptPaulSuggestionAction` function with:

```ts
export async function acceptPaulSuggestionAction(
  matchId: number,
  groupId: string,
): Promise<AcceptOutcome> {
  try {
    const s = await suggestForMatch(matchId);
    await saveBet(matchId, s.scoreT1, s.scoreT2);
    revalidatePath(`/group/${groupId}`);
    return { ok: true, scoreT1: s.scoreT1, scoreT2: s.scoreT2, reasoning: s.reasoning };
  } catch (e) {
    if (e instanceof ApiError) return { ok: false, locked: e.status === 423 };
    return { ok: false, locked: false };
  }
}
```

(The `ignoreLockedRace` import is no longer used by this function — leave it if `saveBetAction` still uses it, which it does. Do not remove it.)

- [ ] **Step 2: Update the knockout action**

In `frontend/app/knockout/[roundId]/actions.ts`, add the same imports:

```ts
import type { AcceptOutcome } from "@/lib/paul-feedback";
import { ApiError } from "@/lib/api/client";
```

Replace the whole `acceptPaulSuggestionAction` function with:

```ts
export async function acceptPaulSuggestionAction(
  matchId: number,
  roundCode: string,
): Promise<AcceptOutcome> {
  try {
    const s = await suggestForMatch(matchId);
    await saveBet(matchId, s.scoreT1, s.scoreT2);
    revalidatePath(`/knockout/${roundCode}`);
    return { ok: true, scoreT1: s.scoreT1, scoreT2: s.scoreT2, reasoning: s.reasoning };
  } catch (e) {
    if (e instanceof ApiError) return { ok: false, locked: e.status === 423 };
    return { ok: false, locked: false };
  }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `pnpm typecheck`
Expected: PASS (exit 0). The drill-in components still type the prop as `=> Promise<void>` — TypeScript allows passing a `Promise<AcceptOutcome>`-returning function where `Promise<void>` is expected (void is assignable from any return), so this stays green until Task 6 tightens the prop type.

- [ ] **Step 4: Commit**

```bash
git add "frontend/app/group/[groupId]/actions.ts" "frontend/app/knockout/[roundId]/actions.ts"
git commit -m "feat(paul): accept-suggestion actions return structured outcome"
```

---

## Task 6: Wire feedback into the drill-in components

**Files:**
- Modify: `frontend/components/group/GroupDrillIn.tsx`
- Modify: `frontend/components/knockout/KnockoutDrillIn.tsx`

These are `"use client"` components combining `useTransition`, `useTranslations`, and server-action calls — not meaningfully unit-testable in isolation (the decision logic they call is covered by Task 1; the panel by Task 3; the row by Task 4). Verify by typecheck + manual run.

- [ ] **Step 1: Update `GroupDrillIn.tsx`**

Replace the import block and the component body. New imports (top of file):

```tsx
"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { MatchView } from "@/lib/api/bracket";
import { formatMatchDateTime } from "@/lib/format-datetime";
import { MatchRow } from "./MatchRow";
import { NumpadScoreInput } from "./NumpadScoreInput";
import { PaulReasoningPanel } from "./PaulReasoningPanel";
import {
  singleMatchFeedback,
  pickKey,
  KEPT_HEADER_KEYS,
  type AcceptOutcome,
  type SingleFeedback,
} from "@/lib/paul-feedback";
```

Change the `acceptPaulAction` prop type from:

```tsx
  acceptPaulAction: (matchId: number, gid: string) => Promise<void>;
```
to:
```tsx
  acceptPaulAction: (matchId: number, gid: string) => Promise<AcceptOutcome>;
```

Inside the component, replace the state declarations:

```tsx
  const tGroup = useTranslations("group");
  const [editing, setEditing] = useState<{ matchId: number } | null>(null);
  const [, startTransition] = useTransition();
```
with (note the added feedback + pending state, and a stored kept-header key so the random funny line doesn't reshuffle on re-render):

```tsx
  const tGroup = useTranslations("group");
  const [editing, setEditing] = useState<{ matchId: number } | null>(null);
  const [startPending, startTransition] = useTransition();
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [feedback, setFeedback] = useState<Map<number, { fb: SingleFeedback; keptKey: string }>>(
    new Map(),
  );

  const dismiss = (matchId: number) =>
    setFeedback((prev) => {
      const next = new Map(prev);
      next.delete(matchId);
      return next;
    });
```

Replace the `onAskPaul` handler:

```tsx
            onAskPaul={() => {
              if (locked) return;
              startTransition(() => {
                acceptPaulAction(m.id, groupId);
              });
            }}
```
with:
```tsx
            paulPending={pendingId === m.id}
            feedback={renderFeedback(m.id)}
            onAskPaul={() => {
              if (locked || pendingId === m.id) return;
              const prior = { t1: m.betScoreT1, t2: m.betScoreT2 };
              setPendingId(m.id);
              startTransition(async () => {
                const outcome = await acceptPaulAction(m.id, groupId);
                const fb = singleMatchFeedback(prior, outcome);
                const keptKey = pickKey(KEPT_HEADER_KEYS, Math.random());
                setFeedback((prev) => new Map(prev).set(m.id, { fb, keptKey }));
                setPendingId(null);
              });
            }}
```

Add a `renderFeedback` helper inside the component, just before the `return (` statement:

```tsx
  function renderFeedback(matchId: number) {
    const entry = feedback.get(matchId);
    if (!entry) return null;
    const { fb, keptKey } = entry;
    if (fb.kind === "locked")
      return (
        <PaulReasoningPanel
          header={tGroup("paulLocked")}
          dismissLabel={tGroup("paulDismiss")}
          onDismiss={() => dismiss(matchId)}
          tone="error"
        />
      );
    if (fb.kind === "error")
      return (
        <PaulReasoningPanel
          header={tGroup("paulError")}
          dismissLabel={tGroup("paulDismiss")}
          onDismiss={() => dismiss(matchId)}
          tone="error"
        />
      );
    const header =
      fb.kind === "kept"
        ? tGroup(keptKey)
        : tGroup("paulSaid", { t1: fb.scoreT1, t2: fb.scoreT2 });
    return (
      <PaulReasoningPanel
        header={header}
        reasoning={fb.reasoning}
        dismissLabel={tGroup("paulDismiss")}
        onDismiss={() => dismiss(matchId)}
      />
    );
  }
```

> `startPending` is unused on purpose elsewhere; if the linter flags it, change the destructure to `const [, startTransition] = useTransition();` and keep `pendingId` as the spinner source. The pending spinner is driven by `pendingId`, not the transition flag.

- [ ] **Step 2: Update `KnockoutDrillIn.tsx`**

Apply the exact same changes, adapted to its props (`roundCode` instead of `groupId`, and the existing `m.team1Code == null || m.team2Code == null` guard). New imports (top):

```tsx
"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { MatchView } from "@/lib/api/bracket";
import { formatMatchDateTime } from "@/lib/format-datetime";
import { MatchRow } from "@/components/group/MatchRow";
import { NumpadScoreInput } from "@/components/group/NumpadScoreInput";
import { PaulReasoningPanel } from "@/components/group/PaulReasoningPanel";
import {
  singleMatchFeedback,
  pickKey,
  KEPT_HEADER_KEYS,
  type AcceptOutcome,
  type SingleFeedback,
} from "@/lib/paul-feedback";
```

Change the `acceptPaulAction` prop type to:

```tsx
  acceptPaulAction: (matchId: number, roundCode: string) => Promise<AcceptOutcome>;
```

Replace the state declarations (same as group: add `pendingId`, `feedback`, `dismiss`). Replace the `onAskPaul` handler:

```tsx
            onAskPaul={() => {
              if (locked || m.team1Code == null || m.team2Code == null) return;
              startTransition(() => {
                acceptPaulAction(m.id, roundCode);
              });
            }}
```
with:
```tsx
            paulPending={pendingId === m.id}
            feedback={renderFeedback(m.id)}
            onAskPaul={() => {
              if (locked || pendingId === m.id || m.team1Code == null || m.team2Code == null) return;
              const prior = { t1: m.betScoreT1, t2: m.betScoreT2 };
              setPendingId(m.id);
              startTransition(async () => {
                const outcome = await acceptPaulAction(m.id, roundCode);
                const fb = singleMatchFeedback(prior, outcome);
                const keptKey = pickKey(KEPT_HEADER_KEYS, Math.random());
                setFeedback((prev) => new Map(prev).set(m.id, { fb, keptKey }));
                setPendingId(null);
              });
            }}
```

Add the identical `renderFeedback` helper (same body as group — it reads `tGroup`, which both components already declare via `useTranslations("group")`).

- [ ] **Step 3: Verify it compiles**

Run: `pnpm typecheck`
Expected: PASS (exit 0).

- [ ] **Step 4: Run the full unit suite (nothing regressed)**

Run: `pnpm vitest run`
Expected: PASS — all suites green, including the existing ones.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/group/GroupDrillIn.tsx frontend/components/knockout/KnockoutDrillIn.tsx
git commit -m "feat(paul): show inline reasoning + pending state on Ask Paul"
```

---

## Task 7: Fill-all summary message

**Files:**
- Modify: `frontend/components/lobby/PaulFillAllButton.tsx`
- Test: `frontend/components/lobby/PaulFillAllButton.test.tsx` (create)

- [ ] **Step 1: Write the failing test**

Create `frontend/components/lobby/PaulFillAllButton.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";

const paulFillAllAction = vi.fn();
vi.mock("@/app/home/actions", () => ({ paulFillAllAction: () => paulFillAllAction() }));

import { PaulFillAllButton } from "./PaulFillAllButton";

const messages = {
  lobby: {
    askPaulFillAll: "PAUL LLENA TODO",
    fillLocked: "Las apuestas están cerradas para esta ronda.",
    fillDone: "🐙 Paul rellenó {count} pronósticos. ¡Suerte!",
    fillNothing1: "🐙 Ya tenías todo listo. Paul se echó una siesta.",
    fillNothing2: "🐙 Nada que rellenar — ¡vas adelantado!",
    fillError: "Paul no pudo rellenar. Intenta de nuevo.",
  },
};

function renderButton() {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <PaulFillAllButton />
    </NextIntlClientProvider>,
  );
}

describe("PaulFillAllButton", () => {
  it("shows the count when Paul fills picks", async () => {
    paulFillAllAction.mockResolvedValueOnce({ ok: true, created: 3 });
    renderButton();
    await userEvent.click(screen.getByRole("button", { name: /paul llena todo/i }));
    expect(await screen.findByText(/rellenó 3 pronósticos/i)).toBeInTheDocument();
  });

  it("shows a funny message when nothing was filled", async () => {
    paulFillAllAction.mockResolvedValueOnce({ ok: true, created: 0 });
    renderButton();
    await userEvent.click(screen.getByRole("button", { name: /paul llena todo/i }));
    expect(await screen.findByText(/(ya tenías todo listo|nada que rellenar)/i)).toBeInTheDocument();
  });

  it("shows the locked notice on a locked round", async () => {
    paulFillAllAction.mockResolvedValueOnce({ ok: false, locked: true, error: "x" });
    renderButton();
    await userEvent.click(screen.getByRole("button", { name: /paul llena todo/i }));
    expect(await screen.findByText(/apuestas están cerradas/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm vitest run components/lobby/PaulFillAllButton.test.tsx`
Expected: FAIL — the count / nothing messages are not rendered (the button only renders the locked case today). The locked test may already pass; the first two must fail.

- [ ] **Step 3: Update `PaulFillAllButton.tsx`**

Replace the file contents with:

```tsx
"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import { PaulBadge } from "@/components/PaulMascot";
import { paulFillAllAction, type PaulFillResult } from "@/app/home/actions";
import { fillAllFeedback, pickKey, FILL_NOTHING_KEYS } from "@/lib/paul-feedback";

/**
 * Hero CTA — red poster, Paul badge + uppercase display label. Used in the
 * lobby's action row. After running, surfaces a result line under the button:
 * how many picks Paul filled, a funny line when there was nothing to fill, the
 * locked notice (backend 423), or a generic error.
 */
export function PaulFillAllButton() {
  const t = useTranslations("lobby");
  const [pending, start] = useTransition();
  const [result, setResult] = useState<{ value: PaulFillResult; nothingKey: string } | null>(null);

  function resultLine() {
    if (!result) return null;
    const fb = fillAllFeedback(result.value);
    switch (fb.kind) {
      case "filled":
        return t("fillDone", { count: fb.count });
      case "nothing":
        return t(result.nothingKey);
      case "locked":
        return t("fillLocked");
      case "error":
        return t("fillError");
    }
  }

  const line = resultLine();
  const isError = result != null && (!result.value.ok);

  return (
    <div className="flex w-full flex-col gap-1.5">
      <button
        type="button"
        onClick={() =>
          start(async () => {
            const value = await paulFillAllAction();
            setResult({ value, nothingKey: pickKey(FILL_NOTHING_KEYS, Math.random()) });
          })
        }
        disabled={pending}
        className="flex w-full items-center justify-center gap-2.5 bg-[var(--color-accent-red)] px-4 py-3.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-inverse)] disabled:opacity-50 hover:bg-[var(--color-bg-ink)]"
      >
        <PaulBadge size={22} />
        {t("askPaulFillAll")}
      </button>
      {line && (
        <span
          className={`chrome-label text-center ${
            isError ? "text-[var(--color-accent-red)]" : "text-[var(--color-text-primary)]"
          }`}
        >
          {line}
        </span>
      )}
    </div>
  );
}
```

> `fillAllFeedback` accepts a `FillOutcome`; `PaulFillResult` from `app/home/actions` is structurally identical (`{ ok: true; created } | { ok: false; locked; error }`), so TypeScript accepts it directly.

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm vitest run components/lobby/PaulFillAllButton.test.tsx`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/components/lobby/PaulFillAllButton.tsx frontend/components/lobby/PaulFillAllButton.test.tsx
git commit -m "feat(paul): fill-all result summary message"
```

---

## Task 8: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the entire unit suite**

Run: `pnpm vitest run`
Expected: PASS — all suites green (new: paul-feedback, PaulReasoningPanel, MatchRow, PaulFillAllButton).

- [ ] **Step 2: Typecheck**

Run: `pnpm typecheck`
Expected: exit 0, no output.

- [ ] **Step 3: Lint**

Run: `pnpm lint`
Expected: 0 errors (the pre-existing `app/layout.tsx` no-page-custom-font warning is allowed). If a new "unused var" error appears for `startPending` in a drill-in, change that destructure to `const [, startTransition] = useTransition();` and re-run.

- [ ] **Step 4: Manual smoke (optional but recommended)**

Use the `run` skill (or the project's documented dev command) to start the frontend, sign in, and:
- On a group, tap "Paul decide" on an empty match → spinner on the row, score fills, reasoning panel appears under the row; dismiss works.
- Tap "Paul" again on that now-filled match where Paul agrees → "de acuerdo" funny header + reasoning.
- In the lobby, tap "Paul llena todo" with empty picks → "Paul rellenó N pronósticos"; tap again with everything filled → the funny "nothing to fill" line.

- [ ] **Step 5: Final commit (if anything was touched in Step 3/4)**

```bash
git add -A
git commit -m "chore(paul): lint/verification fixups"
```

(Skip if the working tree is already clean.)

---

## Self-Review (completed during planning)

- **Spec coverage:** progress/loading (Task 4 spinner + Task 6 pendingId) ✓; reasoning display inline (Tasks 3+6) ✓; "kept your pick" funny message (Task 1 `kept` + Task 2 `paulAgreed*` + Task 6) ✓; fill-all summary + zero-case funny (Tasks 1/2/7) ✓; error/locked handling (Tasks 1/5/6/7) ✓; i18n es-CO + en parallel (Task 2) ✓; pure tested core (Task 1) ✓; out-of-scope items (per-match fill breakdown, admin job) not touched ✓.
- **Placeholder scan:** none — every code step shows full code.
- **Type consistency:** `AcceptOutcome` / `FillOutcome` / `SingleFeedback` / `FillFeedback` and the functions `singleMatchFeedback` / `fillAllFeedback` / `pickKey` and the constants `KEPT_HEADER_KEYS` / `FILL_NOTHING_KEYS` are defined in Task 1 and used with identical names/shapes in Tasks 5–7. MatchRow props `paulPending` / `feedback` defined in Task 4, consumed in Task 6.

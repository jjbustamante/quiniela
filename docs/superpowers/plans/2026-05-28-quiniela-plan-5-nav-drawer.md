# Plan 5 — Side-drawer navigation

**Status:** Ready for implementation
**Created:** 2026-05-28
**Design:** [`docs/superpowers/specs/2026-05-28-nav-drawer-design.md`](../specs/2026-05-28-nav-drawer-design.md)

> **For agentic workers:** REQUIRED SUB-SKILL — use superpowers:subagent-driven-development (preferred) or superpowers:executing-plans. Frontend-only change; each task is one commit. Gates: `pnpm typecheck`, `pnpm lint`, `pnpm build` (build catches server/client boundary errors that typecheck misses). Unit tests via `pnpm test`. Run all from `frontend/`.

**Goal:** Add a global left side drawer (☰ in the TopBar) for secondary features (Pagos, Resultados) so the 4-tab bottom nav stays the principal features, and give every page a reliable way back to `/home`.

**Architecture:** New `NavDrawer` client component (mirrors the existing `UserMenu` open/close mechanics) rendered by the server-component `TopBar`. TopBar loses its dead `back` prop and its `UserMenu` dropdown (sign-out moves into the drawer; avatar becomes a static mark). Bottom nav is unchanged — the fix is at three call sites that currently pass a wrong `activeKey`. The lobby "Pagos" button is removed (drawer supersedes it). `UserMenu` + its test are deleted.

**Tech Stack:** Next.js 16 App Router, React client components, next-intl, Tailwind, Vitest + RTL, Playwright.

---

## Conventions

- All paths are under `frontend/`. Run gates from `frontend/`.
- `NavDrawer` must be `"use client"` (it has state + handlers). `TopBar` stays
  a server component and renders the drawer as a child — exactly how it renders
  `UserMenu` today.
- Role strings are UPPERCASE: `"ADMIN" | "CAPTAIN" | "PLAYER"` (from `session.role`).
- i18n: Spanish in `messages/es-CO.json`, English mirror in `messages/en.json`,
  key parity required. Reuse `common.signOut` ("Cerrar sesión" / "Sign out").
- No `Date.now()` in render (lint rule `react-hooks/purity`); no unused imports.
- Pre-existing acceptable lint warnings: `app/layout.tsx` custom-font + coverage
  artifacts. Don't introduce new ones.

## Progress

- [ ] Task 1: nav i18n keys (menu, home, payments, results)
- [ ] Task 2: NavDrawer component + unit test
- [ ] Task 3: Wire NavDrawer into TopBar; remove `back` prop + UserMenu + avatar dropdown; delete UserMenu (+ test); fix TopBar.test
- [ ] Task 4: Fix BottomNav call sites on the 3 secondary pages (+ captain meta)
- [ ] Task 5: Remove lobby "Pagos" button + now-unused imports
- [ ] Task 6: e2e smoke for the drawer + full verify + ship

---

## Task 1: nav i18n keys

**Files:**
- Modify: `frontend/messages/es-CO.json` (the `nav` object)
- Modify: `frontend/messages/en.json` (the `nav` object)

The `nav` namespace currently holds only the 4 bottom-tab labels. Add four
drawer keys to BOTH files (sign-out reuses `common.signOut`, do not add it here).

- [ ] **Step 1: Add keys to `es-CO.json`**

The current `nav` block is:
```json
  "nav": {
    "myQuiniela": "Mi Quiniela",
    "ranking": "Tabla",
    "matches": "Partidos",
    "compare": "Comparar"
  },
```
Replace it with:
```json
  "nav": {
    "myQuiniela": "Mi Quiniela",
    "ranking": "Tabla",
    "matches": "Partidos",
    "compare": "Comparar",
    "menu": "Menú",
    "home": "Inicio",
    "payments": "Pagos",
    "results": "Resultados"
  },
```

- [ ] **Step 2: Add the mirror keys to `en.json`**

The current `nav` block is:
```json
  "nav": {
    "myQuiniela": "My Bracket",
    "ranking": "Ranking",
    "matches": "Matches",
    "compare": "Compare"
  },
```
Replace it with:
```json
  "nav": {
    "myQuiniela": "My Bracket",
    "ranking": "Ranking",
    "matches": "Matches",
    "compare": "Compare",
    "menu": "Menu",
    "home": "Home",
    "payments": "Payments",
    "results": "Results"
  },
```

- [ ] **Step 3: Verify JSON validity + key parity**

Run:
```bash
cd frontend && node -e "const es=require('./messages/es-CO.json').nav, en=require('./messages/en.json').nav; const a=Object.keys(es).sort(), b=Object.keys(en).sort(); console.log('es:',a.join(',')); console.log('en:',b.join(',')); console.log('parity:', JSON.stringify(a)===JSON.stringify(b));"
```
Expected: both lists identical, `parity: true`.

- [ ] **Step 4: Commit**
```bash
git add frontend/messages/es-CO.json frontend/messages/en.json
git commit -m "feat(frontend): nav drawer i18n keys"
```

---

## Task 2: NavDrawer component + unit test

**Files:**
- Create: `frontend/components/shell/NavDrawer.tsx`
- Create: `frontend/components/shell/NavDrawer.test.tsx`

This mirrors the proven mechanics of `components/shell/UserMenu.tsx` (Escape +
outside-click close, poster styling) but as a left drawer with role-gated nav
items and a sign-out form.

- [ ] **Step 1: Write the failing test**

Create `frontend/components/shell/NavDrawer.test.tsx`:
```tsx
import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, it, expect, vi } from "vitest";
import { NavDrawer } from "./NavDrawer";

vi.mock("@/app/auth-actions", () => ({
  signOutAction: vi.fn(),
}));

const messages = {
  common: { signOut: "Cerrar sesión" },
  nav: { menu: "Menú", home: "Inicio", payments: "Pagos", results: "Resultados" },
};

function renderDrawer(role: "ADMIN" | "CAPTAIN" | "PLAYER") {
  return render(
    <NextIntlClientProvider locale="es-CO" messages={messages}>
      <NavDrawer role={role} />
    </NextIntlClientProvider>,
  );
}

describe("NavDrawer", () => {
  it("is closed initially — no panel items shown", () => {
    renderDrawer("ADMIN");
    expect(screen.queryByRole("link", { name: /inicio/i })).not.toBeInTheDocument();
  });

  it("opens on trigger click and shows admin items", () => {
    renderDrawer("ADMIN");
    fireEvent.click(screen.getByRole("button", { name: /menú/i }));
    expect(screen.getByRole("link", { name: /inicio/i })).toHaveAttribute("href", "/home");
    expect(screen.getByRole("link", { name: /pagos/i })).toHaveAttribute("href", "/admin/payments");
    expect(screen.getByRole("link", { name: /resultados/i })).toHaveAttribute("href", "/admin/results");
    expect(screen.getByRole("button", { name: /cerrar sesión/i })).toBeInTheDocument();
  });

  it("routes captain Pagos to the captain page and hides Resultados", () => {
    renderDrawer("CAPTAIN");
    fireEvent.click(screen.getByRole("button", { name: /menú/i }));
    expect(screen.getByRole("link", { name: /pagos/i })).toHaveAttribute("href", "/captain/payments");
    expect(screen.queryByRole("link", { name: /resultados/i })).not.toBeInTheDocument();
  });

  it("shows only Inicio + sign-out for a player (no Pagos/Resultados)", () => {
    renderDrawer("PLAYER");
    fireEvent.click(screen.getByRole("button", { name: /menú/i }));
    expect(screen.getByRole("link", { name: /inicio/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /pagos/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /resultados/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /cerrar sesión/i })).toBeInTheDocument();
  });

  it("closes when the trigger is clicked again", () => {
    renderDrawer("ADMIN");
    const trigger = screen.getByRole("button", { name: /menú/i });
    fireEvent.click(trigger);
    expect(screen.getByRole("link", { name: /inicio/i })).toBeInTheDocument();
    fireEvent.click(trigger);
    expect(screen.queryByRole("link", { name: /inicio/i })).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run to verify it fails**
```bash
cd frontend && pnpm test -- NavDrawer 2>&1 | tail -20
```
Expected: FAIL — `NavDrawer` not found / module missing.

- [ ] **Step 3: Implement `NavDrawer.tsx`**

Create `frontend/components/shell/NavDrawer.tsx`:
```tsx
"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { signOutAction } from "@/app/auth-actions";

type Role = "ADMIN" | "CAPTAIN" | "PLAYER";

/**
 * NavDrawer — left slide-in menu for secondary features. The ☰ trigger lives
 * in the TopBar; the 4 principal features stay in the BottomNav. Mirrors
 * UserMenu's open/close mechanics (Escape + backdrop close). Items are
 * role-gated: everyone gets Inicio + sign-out; admin/captain get Pagos
 * (routed by role); admin also gets Resultados.
 */
export function NavDrawer({ role }: { role: Role }) {
  const t = useTranslations("nav");
  const tCommon = useTranslations("common");
  const [open, setOpen] = useState(false);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const close = () => setOpen(false);
  const paymentsHref = role === "ADMIN" ? "/admin/payments" : "/captain/payments";

  const linkClass =
    "block border-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] px-3 py-2.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-primary)] hover:bg-[var(--color-accent-gold)]";

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={t("menu")}
        aria-expanded={open}
        aria-controls="nav-drawer-panel"
        className="flex h-8 w-8 items-center justify-center font-display text-2xl font-extrabold leading-none text-[var(--color-text-inverse)]"
      >
        ☰
      </button>
      {open && (
        <>
          <div
            className="fixed inset-0 z-40 bg-black/40"
            onClick={close}
            aria-hidden="true"
          />
          <div
            id="nav-drawer-panel"
            role="dialog"
            aria-label={t("menu")}
            className="fixed inset-y-0 left-0 z-50 flex w-72 max-w-[80vw] flex-col gap-2 border-r-[1.5px] border-[var(--color-line-ink)] bg-[var(--color-bg-paper)] p-4 shadow-[0_8px_28px_rgba(0,0,0,0.25)]"
          >
            <div className="chrome-label chrome-label-muted mb-2">{t("menu")}</div>
            <Link href="/home" className={linkClass} onClick={close}>
              {t("home")}
            </Link>
            {(role === "ADMIN" || role === "CAPTAIN") && (
              <Link href={paymentsHref} className={linkClass} onClick={close}>
                {t("payments")}
              </Link>
            )}
            {role === "ADMIN" && (
              <Link href="/admin/results" className={linkClass} onClick={close}>
                {t("results")}
              </Link>
            )}
            <div className="my-1 border-t-[1.5px] border-dashed border-[var(--color-line-ink)]" />
            <form action={signOutAction}>
              <button
                type="submit"
                className="w-full bg-[var(--color-bg-ink)] py-2.5 font-display text-sm font-bold uppercase tracking-wide text-[var(--color-text-inverse)] hover:bg-[var(--color-accent-red)]"
              >
                {tCommon("signOut")}
              </button>
            </form>
          </div>
        </>
      )}
    </>
  );
}
```

- [ ] **Step 4: Run to verify it passes**
```bash
cd frontend && pnpm test -- NavDrawer 2>&1 | tail -20
```
Expected: PASS — all 5 cases green.

- [ ] **Step 5: Commit**
```bash
git add frontend/components/shell/NavDrawer.tsx frontend/components/shell/NavDrawer.test.tsx
git commit -m "feat(frontend): NavDrawer component"
```

---

## Task 3: Wire NavDrawer into TopBar; remove back prop, UserMenu, avatar dropdown

**Files:**
- Modify: `frontend/components/shell/TopBar.tsx`
- Delete: `frontend/components/shell/UserMenu.tsx`
- Delete: `frontend/components/shell/UserMenu.test.tsx`
- Modify: `frontend/components/shell/TopBar.test.tsx` (already skip-only; confirm still valid)

- [ ] **Step 1: Rewrite `TopBar.tsx`**

Replace the entire file with:
```tsx
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { PaulBadge } from "@/components/PaulMascot";
import { LocaleSwitcher } from "./LocaleSwitcher";
import { NavDrawer } from "./NavDrawer";

export type TopBarProps = {
  title?: string;
  meta?: React.ReactNode;
};

/**
 * Estadio '26 top bar — ☰ drawer + Paul mascot + gold mono subtitle on the
 * left, red "26" mark + locale switcher + identity initial on the right,
 * host-country tri-color stripe underneath. Async server component; fetches
 * the session and renders the (client) NavDrawer + identity mark when authed.
 */
export async function TopBar({ title, meta }: TopBarProps) {
  const t = await getTranslations("common");
  const session = await auth();
  const displayName = session?.user?.name ?? session?.user?.email ?? null;
  const role = session?.role;
  const initial = (displayName ?? "?").charAt(0).toUpperCase();

  return (
    <header>
      <div className="flex items-center justify-between gap-3 bg-[var(--color-bg-ink)] px-4 py-3 text-[var(--color-text-inverse)]">
        <div className="flex min-w-0 flex-1 items-center gap-2.5">
          {role && <NavDrawer role={role} />}
          <PaulBadge size={32} />
          <div className="min-w-0 flex-1">
            <div className="font-display text-xl font-extrabold uppercase leading-none tracking-tight truncate">
              {title ?? t("appName")}
            </div>
            {meta && (
              <div className="chrome-label mt-1 truncate text-[var(--color-accent-gold)]">
                {meta}
              </div>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <span className="font-display text-2xl font-black leading-none tracking-[-0.04em] text-[var(--color-accent-red)]">
            26
          </span>
          <LocaleSwitcher />
          {displayName && (
            <span
              aria-hidden="true"
              className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--color-accent-gold)] font-display text-sm font-extrabold leading-none text-[var(--color-text-primary)]"
            >
              {initial}
            </span>
          )}
        </div>
      </div>
      <div className="stripe-host"><div /><div /><div /></div>
    </header>
  );
}
```

Notes: `back` prop is gone; `UserMenu` import + usage gone; the avatar is now a
static non-interactive `<span aria-hidden>` (identity mark only).

- [ ] **Step 2: Delete UserMenu + its test**
```bash
cd frontend && git rm components/shell/UserMenu.tsx components/shell/UserMenu.test.tsx
```

- [ ] **Step 3: Confirm TopBar.test.tsx still valid**

`TopBar.test.tsx` currently contains only skipped placeholder tests (no
references to UserMenu or `back`). Read it to confirm — no change needed. If it
references `UserMenu` or `back`, remove those references (it should not).

- [ ] **Step 4: Run unit tests + typecheck**
```bash
cd frontend && pnpm test 2>&1 | tail -15 && pnpm typecheck 2>&1 | tail -3
```
Expected: all tests pass (UserMenu test gone, NavDrawer test green), typecheck clean.

- [ ] **Step 5: Build (catches server/client boundary errors)**
```bash
cd frontend && pnpm build 2>&1 | grep -E "Compiled|error|Error|Failed" | head -10
```
Expected: "Compiled successfully".

- [ ] **Step 6: Commit**
```bash
git add frontend/components/shell/
git commit -m "feat(frontend): TopBar uses NavDrawer; drop back prop + UserMenu dropdown"
```

---

## Task 4: Fix BottomNav call sites on secondary pages

**Files:**
- Modify: `frontend/app/captain/payments/page.tsx`
- Modify: `frontend/app/admin/payments/page.tsx`
- Modify: `frontend/app/admin/results/page.tsx`

`BottomNav`'s `activeKey` is already optional. These three pages pass a WRONG
key today, so the bottom bar falsely highlights a core tab. Pass none.

- [ ] **Step 1: `captain/payments/page.tsx`**

Change `<BottomNav activeKey="ranking" />` to `<BottomNav />`. Also fix the
misleading TopBar meta: change `<TopBar title={t("title")} meta={tNav("ranking")} />`
to `<TopBar title={t("title")} />`. If `tNav` (`getTranslations("nav")`) is now
unused on the page, remove its declaration + keep imports tidy (only remove if
no other `tNav(...)` call remains — grep the file).

- [ ] **Step 2: `admin/payments/page.tsx`**

Change `<BottomNav activeKey="myQuiniela" />` to `<BottomNav />`.

- [ ] **Step 3: `admin/results/page.tsx`**

Change `<BottomNav activeKey="myQuiniela" />` to `<BottomNav />`.

- [ ] **Step 4: Verify gates**
```bash
cd frontend && pnpm typecheck 2>&1 | tail -3 && pnpm lint 2>&1 | tail -6
```
Expected: typecheck clean; no NEW lint errors. If removing `tNav` left an
unused-import or unused-var, fix it.

- [ ] **Step 5: Commit**
```bash
git add frontend/app/captain/payments/page.tsx frontend/app/admin/payments/page.tsx frontend/app/admin/results/page.tsx
git commit -m "fix(frontend): secondary pages stop falsely highlighting a bottom-nav tab"
```

---

## Task 5: Remove lobby "Pagos" button + now-unused imports

**Files:**
- Modify: `frontend/app/home/page.tsx`

The drawer supersedes the lobby Pagos shortcut. Grep confirms `Link` is used
ONLY by this button on this page, and `tPay` only for its label.

- [ ] **Step 1: Remove the button block**

Delete this block from the action row (currently lines ~122–129):
```tsx
          {me.role !== "PLAYER" && (
            <Link
              href={me.role === "ADMIN" ? "/admin/payments" : "/captain/payments"}
              className="bg-[var(--color-bg-paper)] border-[1.5px] border-[var(--color-line-ink)] px-4 py-3.5 font-display text-sm font-extrabold uppercase tracking-[0.04em] text-[var(--color-text-primary)] hover:bg-[var(--color-accent-gold)]"
            >
              {tPay("navLabel")}
            </Link>
          )}
```
The action row then ends with just `<PaulFillAllButton />` + `<InviteFriendsButton ... />`.

- [ ] **Step 2: Remove now-unused imports + binding**

- Remove line 1: `import Link from "next/link";`
- Remove line 29: `const tPay = await getTranslations("payments");`

(Confirm by grep that no other `Link` or `tPay(` usage remains on the page
before removing — Step 4's lint/typecheck will catch a miss.)

- [ ] **Step 3: Verify gates**
```bash
cd frontend && pnpm typecheck 2>&1 | tail -3 && pnpm lint 2>&1 | tail -6
```
Expected: typecheck clean (no "Link is declared but never used" / "tPay ..."),
no new lint errors.

- [ ] **Step 4: Commit**
```bash
git add frontend/app/home/page.tsx
git commit -m "refactor(frontend): drop lobby Pagos button (superseded by nav drawer)"
```

---

## Task 6: e2e smoke for the drawer + full verify + ship

**Files:**
- Modify: `frontend/e2e/smoke.e2e.ts`

The drawer is the new global escape hatch — add a smoke check it opens and shows
"Inicio". The landing page (`/`) is unauthenticated so it has no TopBar/drawer;
the drawer renders on authenticated pages. Since e2e has no seeded session,
assert the drawer is **absent** on the public landing (negative control) — the
authenticated-flow coverage stays manual (Plan 3 Task 6 dry-run). This keeps the
e2e honest without standing up auth in CI.

- [ ] **Step 1: Add a smoke assertion**

Append to the `home page` describe block in `frontend/e2e/smoke.e2e.ts`:
```ts
  test('landing page has no nav drawer trigger (unauthenticated)', async ({ page }) => {
    await page.goto('/');
    // The ☰ drawer lives in the TopBar, which only renders for authenticated
    // sessions. The public landing must not expose it.
    await expect(page.getByRole('button', { name: /menú|menu/i })).toHaveCount(0);
  });
```

- [ ] **Step 2: Run e2e**
```bash
cd frontend && pnpm e2e 2>&1 | tail -20
```
Expected: all smoke tests pass (existing + the new one).

- [ ] **Step 3: Full gate sweep**
```bash
cd frontend && pnpm typecheck 2>&1 | tail -3 && pnpm lint 2>&1 | tail -6 && pnpm test 2>&1 | tail -8 && pnpm build 2>&1 | grep -E "Compiled|error|Error" | head -8
```
Expected: typecheck clean; lint only the 2 pre-existing warnings; unit tests
green (NavDrawer suite present, UserMenu suite gone); build "Compiled successfully".

- [ ] **Step 4: Tick plan checkboxes + commit**

Mark all Progress + verification checkboxes `[x]` in this plan file, then:
```bash
git add docs/superpowers/plans/2026-05-28-quiniela-plan-5-nav-drawer.md frontend/e2e/smoke.e2e.ts
git commit -m "test(frontend): nav drawer smoke + Plan 5 complete"
```

- [ ] **Step 5: Push + watch CI**
```bash
cd /home/juan/Workspace/jjbustamante/quiniela && git push origin master
```
Watch the frontend CI run to green (`gh run watch <id> --exit-status`). Backend
is untouched so only frontend CI matters; it will build → Trivy → deploy to
Cloud Run on `master`.

- [ ] **Step 6: Smoke prod**
```bash
curl -sS -o /dev/null -w "%{http_code}\n" https://laquinieladelospanas.com/
```
Expected: 200. (Full drawer behavior verified in-browser during the manual
dry-run — it needs an authenticated session.)

**Verification:**
- [ ] `pnpm typecheck` clean
- [ ] `pnpm lint` no new warnings/errors
- [ ] `pnpm test` green (NavDrawer suite, no UserMenu suite)
- [ ] `pnpm build` compiles
- [ ] `pnpm e2e` green
- [ ] Frontend CI green on `master`; prod landing returns 200

---

## Out of scope (do not build)

Desktop persistent sidebar, richer profile menu on the avatar, any new admin
features, changes to the 4 bottom-nav tabs.

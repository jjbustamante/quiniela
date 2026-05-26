# Estadio '26 codemod — apply guide

This `frontend-out/` tree mirrors `frontend/` and contains the Estadio '26 redesign translated into your real codebase: Tailwind 4 tokens, next/font wiring, server components, Spanish-first copy. Drop these files over the existing ones, install fonts via npm, and `pnpm dev` should boot the new look.

## What's changed

**Design tokens & shell**
- `app/globals.css` — new palette (`#fef9f1` cream, `#0a0a0a` ink, red/blue/green/gold tri-color), `--font-display`/`--font-sans`/`--font-mono` set directly to Google-hosted family names, `.headline-display`, `.poster`, `.chrome-label`, `.stripe-host` utilities. The old cyan tokens stay aliased so any leftover usage keeps compiling.
- `app/layout.tsx` — loads Archivo, Big Shoulders Display, IBM Plex Mono via a plain `<link rel="stylesheet">` to Google Fonts (with preconnects). I started with `next/font/google` but Turbopack on Next 16 doesn't always resolve the virtual `target.css` it emits — the link tag sidesteps that entirely and works in dev + prod identically.

**New shared component**
- `components/PaulMascot.tsx` — `PaulMascot` (bare octopus SVG, accepts body/eye/ink/accent colors) and `PaulBadge` (the canonical red starburst sticker, the form used in top bars and CTAs).

**Pages**
- `app/page.tsx` — full Landing redesign. Big "¿QUIÉN SABE MÁS DE FÚTBOL ENTRE TUS PANAS?" headline, live stats poster, Google sign-in, invite-only nudge.
- `app/home/page.tsx` — Lobby with the countdown hero (ghost "16" numeral), 3-cell stat strip, group poster grid, ink action row.
- `app/group/[groupId]/page.tsx` — drill-in with the 4-team standings strip + match list.
- `app/auth-error/page.tsx` — hazard-stripe top + "ESTA ES PRIVADA." treatment.
- `app/ranking|matches|compare/page.tsx` — placeholder coming-soon pages styled to match.

**Components**
- `components/AuthButton.tsx` — full-width red poster Google CTA.
- `components/shell/TopBar.tsx` — black masthead, Paul badge, red "26", host-country stripe underneath.
- `components/shell/BottomNav.tsx` — black bar, gold top-border on active tab.
- `components/shell/UserMenu.tsx` — gold initial avatar, poster dropdown.
- `components/shell/LocaleSwitcher.tsx` — mono ES/EN toggle inside the dark masthead.
- `components/lobby/{CountdownChip,PotChip,GroupCard,KnockoutLockedCard,PaulFillAllButton}.tsx` — all reworked as posters; GroupCard takes an optional `teams` array to show flags + codes underneath.
- `components/group/{MatchRow,GroupDrillIn,NumpadScoreInput}.tsx` — three-cell match row with team color tint when filled, dashed Paul action row at the bottom instead of a floating corner badge; numpad becomes a two-numeral selection sheet (active = green poster, inactive = dashed placeholder).
- `components/invite/{InviteFriendsButton,InviteFriendsSheet.tsx}` — both restyled to the poster vocabulary.

**Copy**
- `messages/es-CO.json` and `messages/en.json` — new keys for landing copy, lobby chrome (`countdownTitle`, `countdownHeadline`, `statPot`/`statPaid`/`statProgress`, `groupsCount`, `knockoutsLockedHeadline`, `knockoutsOpen`, `inviteOnlyTitle`/`inviteOnlyBody`, etc.), group (`paulDecide`, `paulChange`, `matchesHeading`), auth-error (`privateHeadlinePart1`, `privateHeadlineAccent`, `whyTitle`, `whyBody`, `genericHeadline`), placeholder (`rankingHeadline`/`matchesHeadline`/`compareHeadline`).

## Apply

From the repo root:

```bash
# 1. Copy the whole tree on top of frontend/
cp -R brain/projects/<this-project>/frontend-out/. frontend/

# 2. No new npm dependencies — fonts load via a stylesheet <link>, so
#    your existing pnpm install is enough.

# 3. Boot it
cd frontend
pnpm dev
```

If you prefer a more conservative roll-out, copy files one folder at a time and check each surface:

1. `app/globals.css` + `app/layout.tsx` + `components/PaulMascot.tsx` first — the foundation. The old cyan tokens are aliased so existing components keep rendering (though they'll look ugly until you swap them).
2. `app/page.tsx` + `components/AuthButton.tsx` — landing is the most-visible win; ship this in isolation to gather feedback.
3. `components/shell/*` + `messages/*` — the masthead + nav appear everywhere; do this and the lobby in the same commit.
4. `app/home/page.tsx` + `components/lobby/*` — lobby experience.
5. `components/group/*` + `app/group/[groupId]/page.tsx` — drill-in + numpad.
6. `app/{auth-error,ranking,matches,compare}/page.tsx` — supporting screens.
7. `components/invite/*` — invite share flow.

## API & behavior — unchanged

These components have the same props, server actions, and i18n keys as before (plus a few extra optional props on `MatchRow`/`GroupCard` for the tints and standings strip). No backend changes required.

- `getMe()` and `getMyBracket()` — same.
- `saveBetAction`/`acceptPaulSuggestionAction`/`paulFillAllAction` — same.
- `auth()`, `signIn("google", ...)`, `signOut(...)` — same.
- next-intl `useTranslations(...)` keys — old ones still work; new ones added.

## Known follow-ups

- `MatchRow` accepts a `team1Hex`/`team2Hex` for the filled-state team tint. The backend's `MatchView` doesn't currently surface this; threading it through (or a small `lib/teamColors.ts` lookup keyed on `team1Code`) would light up the colored tints. Without it, filled rows look fine — they just stay paper-white.
- `app/home/page.tsx` hardcodes some hero copy values for now ("16 días", "$480", "12/16") because the lobby endpoint doesn't yet expose pot total / paid count / days-to-kickoff. Once those land in `getMyBracket()` (or a sibling endpoint), wire them through in place of the literals.
- The Estadio palette exposes `--color-accent-red|blue|green|gold`. The repo-wide accent is the red, but if you ever want to A/B test it (or let groups pick their own), it's a one-line CSS var override.

## Files in this tree

```
frontend-out/
├── README.md
├── app/
│   ├── globals.css
│   ├── layout.tsx
│   ├── page.tsx
│   ├── auth-error/page.tsx
│   ├── compare/page.tsx
│   ├── home/page.tsx
│   ├── matches/page.tsx
│   ├── ranking/page.tsx
│   └── group/[groupId]/page.tsx
├── components/
│   ├── AuthButton.tsx
│   ├── PaulMascot.tsx
│   ├── group/
│   │   ├── GroupDrillIn.tsx
│   │   ├── MatchRow.tsx
│   │   └── NumpadScoreInput.tsx
│   ├── invite/
│   │   ├── InviteFriendsButton.tsx
│   │   └── InviteFriendsSheet.tsx
│   ├── lobby/
│   │   ├── CountdownChip.tsx
│   │   ├── GroupCard.tsx
│   │   ├── KnockoutLockedCard.tsx
│   │   ├── PaulFillAllButton.tsx
│   │   └── PotChip.tsx
│   └── shell/
│       ├── BottomNav.tsx
│       ├── LocaleSwitcher.tsx
│       ├── TopBar.tsx
│       └── UserMenu.tsx
└── messages/
    ├── en.json
    └── es-CO.json
```

---
name: a11y-audit
description: Measure WCAG AA contrast and keyboard focus visibility of the msp-app / SyncChord frontend in Chrome, in both themes, with the injectable audit script .claude/scripts/js/a11yAudit.js. Use when a ticket changes colours, themes, Tailwind classes, hover or focus styles, when its acceptance asks for "AA", "contrast", "readable" or "visible focus", or when the owner asks whether a page is accessible (as in KAN-53, KAN-97, KAN-98).
---

# Contrast and focus audit

Runs inside a `browser-check` session (servers up, signed in). Measure; do not judge colours by eye or from class
names, which failed every time it was tried in KAN-97/98.

## 1. Load the script
Read `.claude/scripts/js/a11yAudit.js` and pass its whole text to `javascript_tool`. It defines `window.__a11y` and
returns `a11y audit ready on <path> (<theme>)`. A full page load drops it; navigating with `__a11y.sweep` or
`history.pushState` + a `popstate` event keeps it.

- `__a11y.sweep(['/', '/rooms', ...])`: every visible text against AA (4.5:1, 3:1 for large text; disabled controls
  and `sr-only` text skipped, `aria-hidden` text measured since it is still seen) and every visible lucide icon
  against 3:1, per page. Split long lists into calls of about
  8 pages; a call that runs too long loses the tab.
- `__a11y.focusInfo(element, indicatorBox)`: the outline (or the border of `indicatorBox`, e.g. the wrapper of an
  input with `focus-within:`) against what is painted around it.
- `__a11y.textOn(element)`, `__a11y.ratio('#71717a', '#09090b')`: single checks.

## 2. What to cover
- **Both themes.** Switch with the sidebar's "Invert Polarity" button, or `localStorage['ui-storage']` and a reload,
  and restore the owner's value at the end. Then `await __a11y.settle()` before measuring.
- **The ticket's pages plus the pages the change touched**, the latter found with `git diff --stat`.
- **States that empty pages hide:** rows of liked tracks or playlists, group chats with other members, shared
  tracks, voice messages, rooms with a lesson, an artist's own tracks. Create them with the `seed-test-data`
  skill; an audit of an empty page proves nothing about its rows.
- **Hover:** `computer` `hover` on the element (by `ref`), wait 2 s, then measure. A JS `:hover` cannot be faked.
- **Keyboard focus:** focus the control before the target (click it, or `.focus()` a harmless one), then press the
  real `Tab` key with `computer`; `element.focus()` from JS does not set `:focus-visible` on buttons. For inputs
  `.focus()` is enough, since inputs always match `:focus-visible`. Check that a mouse click shows no new ring
  where `focus-visible` is used.
- Never press Enter or Space on a file input or click it: the native file dialog blocks the extension. Fill file
  inputs with `file_upload` (paths under the scratchpad work).

## 3. Reading the numbers
- A value of 1.0 to 1.3 right after a theme switch or page load is usually the 300 ms colour fade (`transition`
  on body and layouts), stretched by the throttled automation window. Wait and measure again before fixing.
- `focusInfo` and the 0.15 s Tailwind transitions likewise need `settle()` of 1.5 s or more.
- Decorative graphics are exempt from WCAG's non-text contrast rule: the large faint placeholders
  `text-zinc-300 dark:text-zinc-800` (Search empty state, album cover placeholder `Disc`). Say so in the PR rather
  than darkening them.
- An icon at exactly 1.00 inside an `opacity-0` overlay is not shown; the script skips invisible ancestors, but check
  before reporting one.
- What the script cannot see: layers with `pointer-events: none` (skipped by `elementsFromPoint`) and gradients or
  background images (only `background-color` is read). Text over those is checked by eye and screenshot, and the
  PR says so.
- `focusInfo` reports `outline: 'hidden'` for `focus:outline-hidden` (a transparent outline): the indicator there is
  the border, read from `borderVsBackground`.

## 4. Fixing: patterns that held up
- Muted text: `text-zinc-600 dark:text-zinc-400` (about 7:1 on both page backgrounds). `text-zinc-500` is 4.1:1 on
  either background and fails AA.
- Theme tokens (`text-muted`, `bg-surface`, `border-border`) are set per theme in `frontend/src/index.css`
  (`:root.light` / `:root.dark`); change them there, not per class.
- Tailwind 4 reds are lighter than v3: `text-red-600` passes on the plain light page but not on a red tint; use
  `text-red-700` there. White on `bg-red-700` is 6.4:1; on `bg-red-500` it is 3.8:1.
- **The dark-variant trap:** the app's `dark` variant is `&:is(.dark *)`, so `dark:text-x` out-specifies
  `hover:text-y` (both 0,2,0; the later rule wins). Whenever a class string has `dark:text|bg|border-*` and a
  `hover:` of the same property, add the matching `dark:hover:*`. Scan for it after any class change.
- A plain class without a `dark:` partner applies in both themes; darkening it for light makes dark worse. A regex
  over class strings must only touch strings that already have a `dark:` partner, or add one.
- Focus: inputs `focus:outline-hidden focus:border-zinc-900 dark:focus:border-white`; buttons
  `focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900 dark:focus-visible:outline-white`;
  bordered wrappers `focus-within:border-zinc-900 dark:focus-within:border-white`. Use `focus:outline-hidden`, not
  `focus:outline-none`: only `outline-hidden` keeps an outline in Windows High Contrast mode.
- Controls that appear on hover only (`opacity-0 group-hover:opacity-100`) also need
  `group-focus-within:opacity-100 focus-visible:opacity-100`.
- A file input is `sr-only peer`, never `hidden`; its label shows focus with `peer-focus-visible:`.

## 5. Report
In the PR's Tests section: the pages and themes audited, the data that was present, hover and focus checks with
their ratios, and what was not checked (native dialogs, High Contrast mode itself, states without data).

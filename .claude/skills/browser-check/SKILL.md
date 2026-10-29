---
name: browser-check
description: Run the msp-app / SyncChord backend and Vite frontend locally and check a frontend change in Chrome through the Claude in Chrome extension (pages, canvas rendering, forms, theme), then clean up. Use when a ticket changes something visible in the frontend, when its acceptance criteria describe a user flow, or when the owner asks to verify, try out or screenshot a feature in the browser.
---

# Checking a change in the browser

Load the Chrome tools in **one** `ToolSearch` call (`tabs_context_mcp`, `tabs_create_mcp`, `navigate`, `computer`,
`read_page`, `find`, `get_page_text`, `javascript_tool`, `read_console_messages`, `read_network_requests`,
`resize_window`, `tabs_close_mcp`), then call `tabs_context_mcp`
and work in a new tab. If the extension does not answer after two tries, stop and ask the owner to reconnect it;
do not loop.

## 1. Start the app
From the repo root, each in the background (`run_in_background`), never in the foreground:
1. `docker-compose up -d` (Postgres + MinIO) if they are not running (`docker ps`).
2. `cd backend && ./mvnw spring-boot:run`. Wait with `Monitor` for the log line matching
   `Started MusicAppApplication|APPLICATION FAILED|BUILD FAILURE`, not with sleeps. Flyway runs on start: a
   checksum error means an applied migration was edited (see 5).
3. `cd frontend && npm run dev`. Ready when the output shows `Local:   http://localhost:5173/`.
If a port is taken, check whether it is the owner's own instance before stopping anything; reuse it if it runs the
current branch.

## 2. A user
- Prefer a throwaway local user: `POST http://localhost:8080/auth/register` with
  `{"email":"check-<random>@example.test","password":"<random>","displayName":"Browser check"}` (note: `/auth`, not
  `/api/auth`). Do not print the password or token in chat.
- Sign in through the login page with it. If the owner's browser already has a session on localhost:5173, use it
  only for reading; do not create or change data under the owner's account unless asked.
- Content owned by someone else (visibility, 404 for private data) needs a second user; register one the same way.

## 3. Check
- Walk the ticket's acceptance criteria as a user would: click through the nav, not only deep links. Check both
  themes when the change draws anything (`uiStore` `theme`, toggled in the sidebar of `MainLayout`), and a narrow window
  (`resize_window`) for layout.
- After each step: `read_console_messages` with a pattern for errors, and the network tab for failed requests
  (`read_network_requests`) when something does not show up.
- Prefer `read_page` / `find` / `get_page_text` to screenshots for asserting text. Take screenshots where layout or
  rendering is the point. The first screenshot after a navigation sometimes times out; retry once.
- Never click anything that opens a native `alert`/`confirm`/`prompt`; it blocks the extension. The app's own
  `ConfirmDialog` is fine.
- Timing: the automated window is throttled (about 2 animation frames per second were measured), so FPS or
  animation smoothness measured there means nothing. Measure the cost per call instead
  (`performance.now()` around the function in `javascript_tool`, median of many runs) and say so in the PR.
- Canvas/alphaTab pages: check that the score renders once (no doubled notes after a theme change or re-render),
  that the scroll container has a bounded height, and that the cursor follows. `/dev/tab-canvas` is the dev-only
  demo page.
- File uploads cannot be driven from the extension without a file on disk; test them with an integration test or
  `curl -F` instead and say that in the PR.
- When something fails, fix it, then repeat the whole flow, not only the failing step. Repeat it again after the
  code-reviewer's fixes.

## 4. Clean up
- Delete test data you created through the UI or API (`DELETE` endpoints), then the test user itself
  (`DELETE /api/users/me` with its token; this anonymizes it and runs every `UserDataCleanup`).
- Restore anything you changed in the owner's browser (theme, localStorage keys such as `ui-storage`) and close the
  tabs you opened.
- Stop the servers you started, by port:
  `Get-NetTCPConnection -LocalPort 8080,5173 -State Listen -ErrorAction SilentlyContinue | % { Stop-Process -Id $_.OwningProcess -Force -Confirm:$false }`.
  Leave the docker-compose containers running.

## 5. Local database after editing an unmerged migration
If you changed a migration that was already applied locally (only allowed while its PR is unmerged), Flyway refuses
to start with a checksum mismatch. Drop the objects that migration created and delete its row:
`docker exec <postgres container> psql -U postgres -d music_app_db -c "DROP TABLE ...; DELETE FROM flyway_schema_history WHERE version = '<n>';"`,
then start again. Never do this for a migration that is on `development`.

## 6. Report
In the PR's "Tests" section, list what was checked in the browser (flows, themes, widths) and what was not (e.g.
"a real Guitar Pro file was not tried"). Do not call something verified that was only built or linted.

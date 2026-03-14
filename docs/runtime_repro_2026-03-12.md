# Runtime Repro Report — 2026-03-12

## Scope requested
- Login flow stability
- Trip section access
- Sync-spam behavior and logout regression
- Broad end-to-end smoke audit for related glitches

## Environment
- Docker services running: `backend`, `db`, `redis`, `celery`, `celery-beat`, `web`
- Backend rebuilt and restarted before run
- DB migrated and seeded
- Browser automation: Playwright CLI (session-based)

## Known baseline ("before")
From the reported production/dev logs:
- repeated `Unauthorized: /api/sync/batch/`
- repeated `Unauthorized: /api/auth/me/`
- `Unauthorized: /api/auth/login/`
- conductor trip access failures:
  - `Forbidden: /api/trips/45/`
  - `Not Found: /api/trips/44/`
- user-visible symptoms:
  - conductor cannot enter trip section ("erreur de chargement")
  - login info gets wiped
  - sync button spam logs out user

## Repro checklist executed ("after")

### 1) Browser login + trip detail flow
- Opened `/login`
- Logged in as office staff (`0600000001 / staff123`)
- Navigated to trip detail `/office/trips/44`
- Result: page loads successfully (no trip-load error)

Evidence:
- Network trace: `.playwright-cli/network-2026-03-12T19-10-43-529Z.log`
  - `POST /api/auth/login/ => 200`
  - `GET /api/trips/?page=1 => 200`
  - `GET /api/trips/44/ => 200`
- Screenshot: `output/playwright/after-trip-detail-44.png`

### 2) Conductor login route behavior on web
- Logged in as conductor (`0700000001 / conductor123`)
- Result: redirected to `/unauthorized` (expected web behavior)

Evidence:
- Screenshot: `output/playwright/conductor-web-unauthorized.png`

### 3) Mobile-equivalent API flow (conductor)
Executed scripted flow:
- `POST /api/auth/login/` (mobile platform + device_id)
- `GET /api/auth/me/`
- `GET /api/trips/?status=scheduled,in_progress`
- `GET /api/trips/{id}/`
- sync spam simulation: `POST /api/sync/batch/` x30 concurrently (same idempotency key)
- `GET /api/auth/me/` again
- `POST /api/auth/token/refresh/`

Result:
- login/me/trips/detail all `200`
- sync spam: `30/30` responses `200`
  - item outcomes: `1 accepted`, `29 duplicate` (expected idempotency)
- still authenticated after spam:
  - `GET /api/auth/me/ => 200`
  - refresh still valid: `POST /api/auth/token/refresh/ => 200`

Evidence:
- `output/playwright/api_repro_results.json`

## Findings from deeper runtime checks

### Confirmed fixed
- No auth-refresh failure loop on login page in clean browser session.
  - Now only initial `GET /api/auth/me => 401` noise appears (expected anonymous bootstrap), without `POST /api/auth/token/refresh => 400` loop.
  - Console evidence: `.playwright-cli/console-2026-03-12T19-07-40-590Z.log`

### Additional issues discovered
1. Docker CLI warnings indicate `REDIS_PASSWORD` not exported in shell when running `docker compose ...`.
   - This does not stop the app, but indicates config hygiene risk for local/dev commands.
2. `seed_data` does not reset passwords for existing users.
   - Existing DB state can cause false login failures unless passwords are manually reset.
3. Web app triggers duplicate `GET /api/auth/me` on login page bootstrap.
   - Likely React StrictMode effect; not a blocker but noisy.
4. Some UI text appears mojibake in snapshot serialization/logs (`NumÃ©ro`, etc.).
   - Needs encoding audit on frontend text pipeline if visible in actual UI.

## Limitation
- Full native Android UI walkthrough was not possible in this environment (no `adb`/emulator tooling available).
- Mobile behavior was validated via equivalent authenticated API flow + sync concurrency test.

## Follow-up fixes applied (same pass)
1. Docker warning hygiene (`REDIS_PASSWORD` interpolation)
   - Added safe default in compose command and aligned `.env.example` with passworded Redis URLs.
   - Validation: `docker compose ps` now runs without unresolved-variable warnings.
2. Deterministic seed credentials
   - `seed_data` now updates existing seeded users and resets known passwords each run.
   - Validation: login for admin/staff/conductor test accounts all return `200` after reseed.
3. Duplicate auth bootstrap calls
   - Web auth bootstrap now skips `/auth/me` when no token and dedupes StrictMode restore calls with module-level cache.
   - Validation: clean login page load made `0` `/api/auth/me` calls in headless check.
4. Encoding/mojibake guard
   - Added `web/scripts/check-encoding.mjs` and wired it into build (`npm run check:encoding`).
   - Validation: build passes with `[encoding] OK`.

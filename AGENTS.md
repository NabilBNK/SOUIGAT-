# AGENTS.md

Operational guide for coding agents working in `C:\Users\Lamine\Desktop\SOUIGAT`.

## 1) Repo Layout

- `backend/`: Django + DRF API, auth, reporting, sync endpoints.
- `web/`: React + TypeScript + Vite portal.
- `mobile/`: Android Kotlin app (Room + Retrofit + WorkManager + Firebase).
- `firebase.json`, `firestore.rules`, `firestore.indexes.json`: Firebase shared-layer config.

## 2) Rule Files Discovery

- `.cursorrules`: **not found**.
- `.cursor/rules/`: **not found**.
- `.github/copilot-instructions.md`: **not found**.
- If these files are added later, treat them as higher-priority local agent policy.

## 3) Port Configuration (Single Source of Truth)

> **`web/.env.local` → `API_PROXY_URL`** is the canonical backend port setting.
> `vite.config.ts` reads this value automatically. **Never hardcode the port elsewhere.**
>
> Default: `API_PROXY_URL=http://127.0.0.1:8002`
>
> If port 8002 is already in use, free it first (see below), rather than changing `API_PROXY_URL`.
> If you must use a different port, update `API_PROXY_URL` in `web/.env.local` AND restart the frontend.

### How to Check / Free Port 8002 (Windows)

```powershell
# Check what is holding port 8002
netstat -ano | findstr :8002
# Kill by PID (replace 12345 with the actual PID)
Stop-Process -Id 12345 -Force
```

## 4) Build / Lint / Test Commands

Run commands from the correct subdirectory unless specified.

### Backend (Django)

- Install deps (venv):
  - Windows: `./.venv/Scripts/python.exe -m pip install -r requirements.txt`
- System checks:
  - `env DB_HOST=sqlite DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1,0.0.0.0 "./.venv/Scripts/python.exe" manage.py check`
- Run server (local sqlite) — port must match `API_PROXY_URL` in `web/.env.local`:
  - `env DB_HOST=sqlite DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1,0.0.0.0 "./.venv/Scripts/python.exe" manage.py runserver 0.0.0.0:8002`
- Run full test suite:
  - `env DB_HOST=sqlite "./.venv/Scripts/python.exe" manage.py test`
- Run a single test file:
  - `env DB_HOST=sqlite "./.venv/Scripts/python.exe" manage.py test api.tests.test_auth`
- Run a single test class:
  - `env DB_HOST=sqlite "./.venv/Scripts/python.exe" manage.py test api.tests.test_trip_lifecycle.TripLifecycleTests`
- Run a single test method:
  - `env DB_HOST=sqlite "./.venv/Scripts/python.exe" manage.py test api.tests.test_auth.AuthTests.test_login_success`

### Web (React + Vite)

- Install deps:
  - `npm install`
- Dev server (reads `API_PROXY_URL` from `web/.env.local` automatically):
  - `npm run dev -- --host 0.0.0.0 --port 5173`
- Lint:
  - `npm run lint`
- Production build (includes encoding + typecheck):
  - `npm run build`
- Preview build:
  - `npm run preview`

### Web E2E (Playwright)

- Install browsers (first time):
  - `npx playwright install`
- Run all E2E tests:
  - `npx playwright test`
- Run a single spec:
  - `npx playwright test e2e/auth.rbac.spec.ts`
- Run a single test by title:
  - `npx playwright test e2e/auth.rbac.spec.ts -g "Cargo"`

### Mobile (Android)

- Build debug APK:
  - `gradlew.bat assembleDebug`
- Install on connected device:
  - `gradlew.bat installDebug`
- Run unit tests:
  - `gradlew.bat testDebugUnitTest`
- Run one unit test class:
  - `gradlew.bat testDebugUnitTest --tests "com.souigat.mobile.util.CurrencyFormatterTest"`
- Run one unit test method:
  - `gradlew.bat testDebugUnitTest --tests "com.souigat.mobile.util.CurrencyFormatterTest.parseCurrencyInput_handlesNarrowNbsp"`
- Run instrumentation tests:
  - `gradlew.bat connectedDebugAndroidTest`
- Run one instrumentation test class:
  - `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.souigat.mobile.data.local.dao.PassengerTicketDaoTest`

### Firebase CLI / MCP

- Auth status:
  - `npx -y firebase-tools@latest login:list`
- List projects:
  - `npx -y firebase-tools@latest projects:list --json`
- Deploy rules/indexes:
  - `npx -y firebase-tools@latest deploy --only firestore:rules,firestore:indexes --project souigat-6be49`
- Start Firebase MCP server:
  - `npx -y firebase-tools@latest mcp`

## 5) Local-First Architecture Constraints

- Do **not** bypass backend or Room for core writes.
- Canonical write flow is local/backend first, Firebase mirror second.
- Web sync to Firestore must be asynchronous and non-blocking for user actions.
- Mobile reads can prefer Firestore but must keep backend fallback.

## 6) Language-Specific Style

### TypeScript / React (web)

- Use strict typing; avoid `any` unless unavoidable.
- Prefer `type` imports where appropriate (`import type { X } ...`).
- Keep API access inside `web/src/api/*` modules.
- Keep sync-related logic under `web/src/sync/*` and Firebase bootstrapping under `web/src/firebase/*`.
- Use camelCase for vars/functions; PascalCase for components/types.
- Use named exports for helpers; default exports only where already established.
- Prefer guard clauses and explicit error branches over deep nesting.
- Log warnings for recoverable sync failures; do not swallow critical exceptions silently.

### Python / Django (backend)

- Follow existing DRF structure: serializers handle validation shape, views orchestrate behavior.
- Use explicit permission decorators/classes for protected endpoints.
- Return stable JSON error payloads and proper HTTP statuses.
- Keep business logic in `api/services/*` where possible.
- Use snake_case for functions/variables; PascalCase for classes.
- Add concise docstrings for non-obvious view/service behavior.
- Avoid expensive DB loops; prefer queryset filtering/annotation patterns.

### Kotlin / Android (mobile)

- Prefer constructor injection with Hilt.
- Keep repository APIs returning `Result<T>` and map failures to typed domain exceptions.
- Use immutable data classes and explicit DTO mapping.
- Use `Timber` for logging; avoid noisy logs in hot paths.
- Keep Room entities in `data/local/entity`, DAO logic in `data/local/dao`.
- Keep Firebase session/read logic in `data/firebase`.
- Respect existing package naming and build-variant behavior.

## 7) Imports and Formatting

- Keep imports grouped and minimal; remove unused imports.
- Match existing formatting in each file; do not introduce a new formatter style.
- Keep line length reasonable and readability-first.
- Avoid comment noise; add comments only for non-obvious logic.

## 8) Naming and API Conventions

- Prefer descriptive names (`queueTripUpsert`, `ensureSignedIn`) over abbreviations.
- Use consistent suffixes:
  - `*Dto` for network DTOs
  - `*Entity` for Room models
  - `*Repository` / `*RepositoryImpl` for data layers
  - `*Serializer` for DRF serializers
- Maintain stable endpoint naming under `/api/...`.

## 9) Error Handling and Retries

- Distinguish retryable vs terminal errors in sync code.
- Track status transitions explicitly (`pending`, `in_progress`, `synced`, `failed`, `conflict`).
- Include enough context in logs (entity, id, op) without leaking secrets.
- Never log tokens, passwords, service account JSON, or private keys.

## 10) Security Practices

- Keep credentials in env files, never hardcode secrets.
- Respect Firestore claims/rules model (role + office/conductor scope).
- Backend custom token endpoint must require authenticated user.
- Do not commit private keys, service-account files, or `.env` secrets.

## 11) Agent Workflow Expectations

- Make incremental, focused changes; avoid broad rewrites.
- Validate with the smallest relevant command first, then broader checks.
- If touching multiple stacks, run at least one verification command per touched stack.
- When uncertain about behavior, inspect adjacent code and follow existing patterns.

# Protocol: Playwright RBAC Tests
## Overview
The goal is to implement 7 critical Playwright end-to-end tests to verify the Role-Based Access Control (RBAC) in the React frontend. These tests go beyond happy-path UI guards; they test real-world scenarios including office-scoped visibility, conductor portal bans, and true network-level token expiration handling.

## Project Type
WEB

## Success Criteria
- [ ] 7 distinct E2E tests execute and pass successfully in isolated browser contexts.
- [ ] A reliable test database seeding strategy is established.
- [ ] Unauthenticated users are appropriately bounced to login.
- [ ] Role guarding properly enforces rules (Admin vs Office Staff vs Conductor).
- [ ] Cross-office data access is blocked in the UI.
- [ ] Token expiration triggers a login redirect via network interception, not just local storage tampering.

## Tech Stack
- **Framework:** Playwright (Node.js/TypeScript)
- **Target:** SOUIGAT Frontend Web App (**Vite 6 / React 19**)
- **Rationale:** Playwright natively supports deep E2E flow testing, network traffic interception (`page.route()`), and browser context isolation so tests do not bleed sessions into one another.

## File Structure & Setup
```
frontend/
├── playwright.config.ts    # Configured for Vite dev server (e.g., npm run dev)
├── e2e/
│   ├── global-setup.ts     # Seeds the database with known test users & trips via API or Django command
│   └── auth.rbac.spec.ts   # Uses test.describe blocks for state isolation
```

## Task Breakdown

### Task 1: Initialize Playwright & Setup Global DB Seed
**Agent:** `test-engineer`
**Skills:** `webapp-testing`
- **ACTION:** 
  1. Install Playwright and configure `playwright.config.ts` for Vite React base URLs.
  2. Restrict the `projects` array in the config to **Chromium only** to accelerate testing and avoid redundant cross-browser noise.
  3. Create a `global-setup.ts` file that uses `child_process.exec` to run a Django management command (e.g., `python manage.py seed_test_db`) or hits an API endpoint to ensure standard test users (`admin`, `staff_algiers`, `staff_oran`, `conductor`) and trips exist.
- **VERIFY:** Config passes and `global-setup` executes without failure.

### Task 2: Implement Test - Unauthenticated Redirect
**Agent:** `test-engineer`
**ACTION:** Write a test attempting to navigate to an authenticated route (e.g., `/trips` or `/admin`) without logging in.
- **VERIFY:** Assert the URL redirects to `/login`.

### Task 3: Implement Tests - Role Enforcement (Admin vs Office Staff)
**Agent:** `test-engineer`
- **ACTION:** 
  1. Load the randomized admin URL from the `DJANGO_ADMIN_PATH` environment variable. (Do NOT hardcode `/admin/` as it will 404).
  2. In a `test.describe` block (for isolation), login as an `office_staff` user and attempt to navigate to the dynamic admin path. Assert access is denied or redirected to dashboard.
  3. Login as an `admin` user and navigate to the dynamic admin path. Assert access is granted.

### Task 4: Implement Tests - Office Scope Enforcement (The SOUIGAT Rule)
**Agent:** `test-engineer`
- **ACTION:** 
  1. Login as `staff_oran`. 
  2. Set up parallel interception (`page.waitForResponse`) for the `/api/trips/` endpoint.
  3. Navigate to `/trips`. 
  4. Parse `response.json()` and assert that a known `Algiers`-only trip ID is completely absent from the backend data payload. (Security-meaningful assertion).
  5. Assert the trip is also NOT visible in the rendered UI.

### Task 5: Implement UI Guard & Test - Conductor UI Ban
**Agent:** `frontend-specialist` (Implementation) -> `test-engineer` (Test)
- **ACTION:** 
  1. **Prerequisite:** Verify and implement a strict route guard in the React frontend that explicitly checks `role === 'conductor'` and kicks them out to `/login` (conductors are mobile-only).
  2. Login as `conductor` using web credentials.
  3. Assert the frontend catches this role and immediately rejects the login session for web access.

### Task 6: Implement Tests - Session Management (Logout)
**Agent:** `test-engineer`
- **ACTION:** 
  1. Login, click "Logout".
  2. Attempt to navigate backward or manually load a protected route.
  3. Assert redirect to login.

### Task 7: Implement Test - True Token Expiration via Network Interception
**Agent:** `test-engineer`
- **ACTION:** 
  1. Login successfully.
  2. Use `await page.route('**/api/**', route => route.fulfill({ status: 401, body: '{"code":"token_not_valid"}' }))` to simulate the backend rejecting a suddenly expired token.
  3. Attempt an action that triggers an API call (e.g., navigating to trips or clicking refresh).
  4. Assert the Axios interceptor catches the 401 and forces the user back to the `/login` page.

## Phase X: Verification
- [ ] Run `python .agent/skills/webapp-testing/scripts/playwright_runner.py` (or `npx playwright test`).
- [ ] Verify 7/7 tests pass against the Vite dev server and correctly rely on seeded DB state.
- [ ] Run `npm run lint` on the frontend.

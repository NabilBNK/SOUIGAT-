# Role Architecture Fix Plan (Orchestration Phase)

## 1. Security Model Definition
The RBAC fix separates authorization, scoping, and business rules:
- **Authorization ("Can I do X?"):** Handled entirely by `MatrixPermission` checking actions against `PERMISSION_MATRIX`.
- **Scoping ("Which records can I see/touch?"):** Handled by `OfficeScopePermission` and QuerySet filtering (e.g., `admin` sees all, `office_staff` sees their own office).
- **Business Rules ("Does this object allow X right now?"):** Handled by explicit workflow checks (e.g., `TripStatusPermission`, `_enforce_destination_office()`).

**Project Type**: System-wide (BACKEND + WEB)

## 2. Success Criteria
- [ ] Views explicitly declare `required_actions` instead of checking `user.role` or `user.department` for authorization contexts.
- [ ] Scoping logic (checking `user.role == 'admin'` for query filtering) is kept unchanged or migrated appropriately.
- [ ] A request-level cache (`request._permset`) minimizes DB/CPU overhead during permission resolution.
- [ ] Cargo action mappings (`create_cargo_ticket`, `transition_cargo_status`, `deliver_cargo`) are explicit and don't break the destination office logic.
- [ ] Unspecced roles (`driver`, `passenger`) are safely soft-disabled (`is_active=False`) without auto-promotion.
- [ ] New security test matrix verifies exact access controls (403 vs 200) AND data leakage (wrong office = 403 or empty list).

## 3. Tech Stack
- **Backend**: Django REST Framework (Python)
- **Frontend**: React + TypeScript 

## 4. File Structure
- `backend/api/models/user.py`
- `backend/api/permissions.py`
- `backend/api/views/trip_views.py`
- `backend/api/views/ticket_views.py`
- `backend/api/views/report_views.py`
- `backend/api/views/export_views.py`
- `backend/api/views/cargo_views.py`
- `backend/api/views/expense_views.py`
- `backend/api/views/sync_views.py`
- `backend/api/migrations/XXXX_disable_legacy_roles.py` (New)
- `backend/api/tests/test_permissions.py` (New)
- `backend/api/tests/test_scoping.py` (New)
- `web/src/types/auth.ts`
- `web/src/components/layout/Sidebar.tsx` (UX routing guards)

## 5. Phase 2: Implementation Tasks

### Task 1: Core Permission Framework
- **Agent**: `backend-specialist`
- **OUTPUT**: 
  - Update `get_user_permissions()` to cache its result on the request object (`request._permset`) to avoid redundant matrix checks. 
  - Create `MatrixPermission` to enforce DRF view methods mapped to `PERMISSION_MATRIX` actions (replacing `RBACPermission` where action granularity is needed).
  - Explicitly define the mapping for cargo actions (e.g., adding a specific `deliver_cargo` string to the matrix and views).

### Task 2: Apply Matrix Framework to Sensitive Views
- **Agent**: `backend-specialist`
- **OUTPUT**: 
  - Apply `MatrixPermission` incrementally across sensitive endpoints: `report_views`, `export_views`, `trip_views`, `ticket_views`, `expense_views`, `sync_views`, `cargo_views`.
  - Maintain the existing `OfficeScopePermission` checks.
  - Review `get_queryset` implementations to ensure `user.role == 'admin'` remains as legitimate DB scoping, removing role checks strictly where they enforce *actions*.

### Task 3: Safe Legacy Role Migration
- **Agent**: `database-architect`
- **OUTPUT**: 
  - Remove `driver` from `ROLE_CHOICES` and `passenger` from department choices.
  - Create a Django data migration softly deactivating (`is_active=False`) these users and outputting a summary to the console for admins, avoiding any privilege escalation.

### Task 4: Fix Frontend UX Routing
- **Agent**: `frontend-specialist`
- **OUTPUT**: 
  - Remove legacy types from `web/src/types/auth.ts`.
  - Update the sidebar routing logic so a guichetier only sees cargo links (explicitly documented as UX-only, relying on Task 2 for actual security).

### Task 5: Security & Scope Regression Engineering
- **Agent**: `test-engineer`
- **OUTPUT**: 
  - Implement full 403/200 test matrix for authorization testing across sensitive endpoints (`cargo` failing on reports, exams, passenger tickets).
  - Implement 403/empty list data leakage tests checking cross-office requests (`office_staff A` trying to read `office B` data).
  - Cargo workflow test covering create -> transition -> deliver at destination office.

## 6. Phase X: Orchestration Verification
- [ ] Backend Linter: `flake8 backend`
- [ ] Security Static Analysis: `pip install bandit && bandit -r backend/api`
- [ ] Execution of new Test Matrix: `pytest backend/api/tests/` passes 100%
- [ ] Synthesis: Ensure all 3 agents (`backend-specialist`, `frontend-specialist`, `test-engineer`) completed their assigned tasks properly.

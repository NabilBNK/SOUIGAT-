# Orchestration Plan: Fixing Trip Assignment and Start Issues

## Phase 1: Analysis & Discovery

**Identified Issues:**
1. **Conductor Blocked by Orphaned Trip (Production Blocker)** 
   - Trip 41/42 cannot start because the assigned conductor (Mohamed Larbi) has an unresolved `in_progress` trip in the database.
   - **Root Cause:** Operational failure or app crash left a trip in the `in_progress` state without being formally completed or cancelled.
   - **Solution:** Immediate database intervention (Task 0) to unblock the conductor, followed by an administrative tool to handle this natively without manual DB intervention.

2. **Poor Error UX on Trip Start Failure**
   - The backend enforces the single active trip rule but returns a generic HTTP 400 with a string error. The web frontend behaves poorly or silently fails to display this context to the operator.
   - **Solution:** Structured JSON error response with an `error_code` from the backend, and specific toast notification handling on the frontend.

3. **Trip missing on Conductor Dashboard (Mobile)**
   - The user reports the new scheduled trip is hidden from the mobile dashboard.
   - **Hypothesized Cause:** Serialization error dropping the trip list.
   - **Action:** Must diagnose with actual device/emulator logs before writing any fix. We must avoid silent failures (dropping unparseable trips), which cause severe data loss in financial systems. The current "Erreur de données" is safer than invisible data loss.

---

## Phase 2: Implementation Plan

### Task 0: Immediate Production Mitigation (Database Architect)
- **Goal:** Unblock the conductor safely.
- **Action (Pre-flight Checklist BEFORE update):**
  1. Verify all passenger tickets have synced: `SELECT COUNT(*) FROM passenger_tickets WHERE trip_id = {orphaned_trip_id} AND synced_at IS NULL` (Must be 0).
  2. Verify all expenses have synced: `SELECT COUNT(*) FROM trip_expenses WHERE trip_id = {orphaned_trip_id} AND synced_at IS NULL` (Must be 0).
  3. Update trip status via Django shell: set `status='completed'` and `completed_at=NOW()`.
  4. Manually insert a record into the `audit_log` detailing who forced the closure and why.

### Group A: Backend Specialist
- **Task A1 (Admin Feature):** Implement a "Force Complete / Cancel" endpoint or admin action for trips stuck in `in_progress`.
  - **Reconciliation Gate:** The endpoint MUST check for any unsynced records (`synced_at IS NULL`). If unsynced records exist, return an error (`TRIP_HAS_PENDING_SYNC`). If none exist, allow completion with a mandatory `force_reason` field that writes to the audit log and sets `completed_at`.
- **Task A2 (Error Formatting):** Update the `/start/` and `/complete/` endpoints in `trip_views.py` to return structured JSON errors including a scoped, machine-readable `error_code` (e.g., `CONDUCTOR_BUSY`). Ensure `detail` does not leak cross-office data.
- **Verification:** Unit tests verifying the structured error format, the reconciliation gate, and the new admin override functionality.

### Group B: Frontend Specialist (Web)
- **Task B1:** Update the trip launch/start button in the React dashboard to handle HTTP 400 safely.
- **Task B2:** Catch the new structured 400 error codes. Display clear toast notifications with actionable steps (e.g., "Conductor is busy. Go to Trips -> [Trip ID] -> Force Complete").
- **Verification:** Verified by checking the UI response to 400 errors.

### Group C: Mobile Developer (Android)
- **Task C1 (Diagnose First):** Pull `TripRepositoryImpl` logs from the emulator or add debug logging to capture the raw JSON response of `TripApi.getTripList()`.
- **Task C2 (Fix):** Once the exact mismatch or error is identified from the logs, fix the DTOs (`TripListDto.kt`) to match the backend.
- **Constraint:** Do NOT implement silent skipping of failed list items. If a trip fails to parse, it must gracefully fail the whole list and show a clear error to the user rather than hiding records.
- **Verification:** Successful compilation and confirmed display of the previously missing trip on the mobile dashboard.

---

## Acceptance Criteria
1. **Unblocked Conductor:** Mohamed Larbi can successfully start a new trip today.
2. **Admin Override:** System administrators can force-complete an orphaned trip via the API/interface without developer intervention.
3. **Structured Errors:** `POST /api/trips/{id}/start/` returns `{"error_code": "CONDUCTOR_ALREADY_ACTIVE", "detail": "..."}` when blocked.
4. **Mobile Visibility:** The mobile dashboard correctly displays all assigned trips without silent data dropping, after confirming the root cause from logs.

---

## ⏸️ ACTION REQUIRED: User Approval
Please review this revised plan. 
- Type **Y** to approve and start Execution.
- Type **N** to reject and ask for further changes.

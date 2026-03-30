# Trip Status Consistency Implementation Plan

## Context

This plan consolidates findings from two parallel analysis agents and defines a concrete implementation path to fix the issue:

- Mobile trip was terminated/completed.
- Web did not consistently reflect termination.
- Backend and web status could diverge under offline/fallback conditions.

## Executive Summary

The core problem is **state divergence**:

- Backend DB is the canonical source for trip lifecycle, but mobile fallback can mark local/Firestore as completed even when backend update fails.
- Web currently mixes backend and Firestore mirror status in a way that can look inconsistent across pages.

Fix strategy:

1. Keep local-first mobile UX.
2. Keep Firebase mirror for fast cross-client visibility.
3. Guarantee backend convergence by adding idempotent `trip_status` replay through sync batch.
4. Harden web status precedence rules (timestamp-aware merge, avoid stale mirror overrides).

---

## Root Causes

### 1) Backend is canonical, but mobile fallback can bypass it

- Backend trip lifecycle writes happen in Django trip endpoints and update DB status/arrival fields.
- Mobile `completeTrip()` fallback path can still return local success after backend failure.
- `trip_status` queue currently targets Firestore mirror writes, not backend lifecycle replay.

Result: backend can remain `in_progress` while mobile and mirror-backed web views show `completed`.

### 2) Trip status queue is not guaranteed to drain immediately

- Trip status queueing does not consistently trigger immediate one-time sync, unlike other entities.
- This introduces delays when relying on periodic worker execution.

### 3) Web data source mixing can cause UI inconsistency

- Trip list/detail may apply mirror override.
- Other dashboard metrics may still use backend-only status.
- No strong timestamp precedence in some paths can allow stale mirror status to display.

---

## Objectives

1. Ensure eventual backend convergence for every `start/complete` action.
2. Preserve responsive offline UX for conductors.
3. Prevent duplicate/late/out-of-order events from corrupting state.
4. Make web status rendering deterministic and conflict-safe.

---

## Implementation Plan

## P0 - Correctness and Convergence (must-have)

### A) Backend: accept and apply `trip_status` in sync batch (idempotent)

Files:

- `backend/api/serializers/sync.py`
- `backend/api/views/sync_views.py`

Changes:

- Add `trip_status` to allowed sync item types.
- In sync item processor, add a `trip_status` handler with idempotent transition application.
- Apply rules:
  - If status already equals target -> success no-op.
  - If incoming event is stale (behind current terminal state) -> success no-op.
  - Only valid forward transitions should mutate state.
  - Preserve safe lifecycle constraints and role checks.

Expected outcome:

- Offline/mobile events eventually reconcile into backend DB.
- Duplicate replay is harmless.

### B) Mobile worker: replay trip status to backend, mirror as secondary

Files:

- `mobile/app/src/main/java/com/souigat/mobile/worker/SyncDataWorker.kt`

Changes:

- For `itemType == "trip_status"`, process in this order:
  1. Attempt backend lifecycle sync (authoritative convergence).
  2. Write Firestore mirror patch (fast UI propagation).
- Keep retry/backoff behavior for transient failures.
- Treat duplicate/already-applied backend responses as synced/no-op.

Expected outcome:

- Status events do not stop at mirror layer.
- Backend no longer drifts indefinitely.

### C) Mobile repository: queue deterministic status events + immediate trigger

Files:

- `mobile/app/src/main/java/com/souigat/mobile/data/repository/TripRepositoryImpl.kt`

Changes:

- Ensure queued `trip_status` payload includes stable transition metadata (`status`, `transition_at`, event id).
- Use deterministic-enough idempotency key per action event.
- After enqueue, trigger immediate one-time sync (`SyncScheduler`) to reduce user-visible lag.
- Tighten fallback behavior so non-retryable lifecycle errors do not silently appear as success.

Expected outcome:

- Faster convergence when network returns.
- Better correctness under permission/validation failures.

---

## P1 - Web Consistency Hardening

### D) Timestamp-aware mirror precedence

Files:

- `web/src/hooks/useTripMirrorData.ts`
- `web/src/pages/office/TripDetail.tsx`
- `web/src/pages/office/TripList.tsx`
- `web/src/pages/office/Dashboard.tsx`

Changes:

- Surface mirror `source_updated_at` for trip status data.
- Apply mirror status only when mirror timestamp is newer than backend `updated_at`.
- Never allow stale mirror values to regress terminal backend states (`completed`, `cancelled`).

Expected outcome:

- Fewer contradictory statuses across screens.
- Stable display when backend and mirror updates arrive out of order.

---

## P2 - Operational Guardrails (recommended)

### E) Diagnostics and observability

Potential files:

- mobile sync diagnostics screen/viewmodel
- backend sync logging enhancements

Changes:

- Show pending `trip_status` queue count, oldest age, last error.
- Add backend logs/metrics for applied/no-op/duplicate trip status events.

Expected outcome:

- Faster incident triage.
- Better confidence in rollout.

---

## Validation Checklist

### Mobile behavior

1. Complete trip while offline -> local shows completed.
2. Reconnect -> queue drains -> backend becomes completed.
3. Duplicate taps -> only one effective transition; duplicates no-op.
4. Kill app with pending queue -> restart -> resume sync and converge.

### Ordering behavior

5. Deliver stale `in_progress` after `completed` -> backend remains completed.
6. Firestore failure but backend available -> backend still converges.

### Web behavior

7. Trip detail/list/dashboard eventually show same effective status.
8. No stale mirror override when backend already newer/terminal.

---

## Validation Commands

### Backend (`backend/`)

```bash
env DB_HOST=sqlite "./.venv/Scripts/python.exe" manage.py check
env DB_HOST=sqlite "./.venv/Scripts/python.exe" manage.py test api.tests.test_batch_sync
env DB_HOST=sqlite "./.venv/Scripts/python.exe" manage.py test api.tests.test_trip_lifecycle
```

### Mobile (`mobile/`)

```bash
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
gradlew.bat installDebug
```

### Web (`web/`)

```bash
npm run lint
npm run build
```

---

## Rollout Sequence

1. Ship backend `trip_status` sync support first.
2. Ship mobile worker/repository replay + immediate trigger.
3. Ship web precedence hardening.
4. Run offline/online QA scenario pack on physical device.
5. Monitor first production window for sync no-op/error rates.

---

## Risks and Mitigations

- **Risk:** Duplicate events create repeated transitions.
  - **Mitigation:** idempotency key + no-op handling for already-applied states.

- **Risk:** Out-of-order delivery regresses status.
  - **Mitigation:** transition validation + stale event no-op.

- **Risk:** User sees delayed web updates.
  - **Mitigation:** immediate one-time sync trigger + mirror fast path.

- **Risk:** UI mismatch between pages.
  - **Mitigation:** consistent status merge logic across list/detail/dashboard.

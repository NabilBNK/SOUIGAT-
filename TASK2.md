# TASK2.md — Verified Real Problems (Filtered)

This file keeps only problems that are still real after checking current code.

## P0 — Critical

### R1) Mobile offline tickets/expenses are not converging to backend DB

**Why this is real**
- Mobile creates queue items for `passenger_ticket`, `cargo_ticket`, and `expense` (`mobile/app/src/main/java/com/souigat/mobile/data/repository/TicketRepositoryImpl.kt`, `mobile/app/src/main/java/com/souigat/mobile/data/repository/ExpenseRepositoryImpl.kt`).
- Worker currently syncs these types to Firestore mirror only, not backend batch endpoint (`mobile/app/src/main/java/com/souigat/mobile/worker/SyncDataWorker.kt`).
- Backend `/api/sync/batch/` already supports these types (`backend/api/views/sync_views.py`).

**Impact**
- Backend reports/settlements can drift from field reality.
- Web may look correct via mirror while backend financial data stays incomplete.

**Minimum fix**
1. In `SyncDataWorker`, route `passenger_ticket` / `cargo_ticket` / `expense` through `SyncApi.syncBatch` first.
2. Keep Firestore write as secondary mirror step.
3. Keep current retry/backoff + terminal classification behavior.

**Acceptance**
- A conductor-created passenger ticket appears in backend DB after queue drain.
- A conductor-created expense appears in backend DB after queue drain.
- Mirror still updates for realtime UI.

---

### R2) Settlement snapshot can become stale after late-arriving synced records

**Why this is real**
- Settlement creation uses `get_or_create` and does not recompute existing snapshots (`backend/api/services/settlements.py`).
- Trip completion paths call settlement initiation once (`backend/api/views/trip_views.py`, `backend/api/views/sync_views.py`).
- If additional valid records land after completion, totals can stay outdated.

**Impact**
- Incorrect `expected_total_cash`, `expenses_to_reimburse`, and `net_cash_expected`.

**Minimum fix**
1. Add a recompute-on-demand service for existing settlements.
2. Trigger recompute when accepted sync items arrive for a completed trip.
3. Mirror updated settlement after recompute.

**Acceptance**
- Settlement totals update when late sync records are accepted.
- No stale snapshot after queue drains.

---

### R3) Web settlement query can fire from mirror-only completion state

**Why this is real**
- Trip detail enables settlement query on `effectiveStatus` (mirror-aware) (`web/src/pages/office/TripDetail.tsx`).
- Mirror may show `completed` before backend trip status converges.

**Impact**
- Premature settlement 404s and misleading settlement actions.

**Minimum fix**
1. Gate settlement query on backend-confirmed status (`trip.status === 'completed'`).
2. If mirror says completed but backend not yet completed, show a "syncing from field" hint instead of settlement actions.

**Acceptance**
- No settlement query while backend trip is not completed.
- No misleading "initiate settlement" prompt during backend convergence.

---

## P1 — Important

### R4) Stuck-sync reset has no durable audit signal

**Why this is real**
- Worker resets `SYNCING -> PENDING` on startup (`mobile/app/src/main/java/com/souigat/mobile/worker/SyncDataWorker.kt`).
- DAO reset operation has no returned count/history (`mobile/app/src/main/java/com/souigat/mobile/data/local/dao/SyncQueueDao.kt`).

**Impact**
- Hard to diagnose repeated crash/interruption loops in the field.

**Minimum fix**
1. Return affected row count from reset query.
2. Log structured details (count + oldest item age + entity types).
3. Expose this in a simple diagnostics surface.

**Acceptance**
- Each startup reset is observable in logs/diagnostics.
- Team can quantify stuck-sync frequency.

---

### R5) Web client sync engine is effectively dead code but UI still shows sync health

**Why this is real**
- `startSyncEngine()` has no call site (`web/src/sync/engine.ts`).
- Operational queue functions are defined but not wired into page flows (`web/src/sync/operationalSync.ts`).
- Header sync badge reads IndexedDB queue status (`web/src/components/Layout/Header.tsx`).

**Impact**
- Sync badge can be misleading because this path is not the active source of truth.

**Minimum fix (choose one)**
1. Remove/deprecate client queue status UI if backend-driven mirror is canonical, **or**
2. Fully wire engine startup + enqueue integration and keep it maintained.

**Acceptance**
- Sync status UI reflects a real active pipeline, not dormant logic.

---

## P2 — Quality Gates

### R6) Missing regression tests for new `trip_status` batch behavior

**Why this is real**
- Backend tests cover batch sync broadly, but no direct `trip_status` cases yet (`backend/api/tests`).
- Mobile worker now has backend-first logic for `trip_status` and needs targeted tests.

**Impact**
- High risk of silent regressions in offline completion convergence.

**Minimum fix**
1. Add backend tests for `trip_status` accepted/duplicate/stale/no-op/quarantine cases.
2. Add mobile unit tests around worker handling of backend response states.

**Acceptance**
- CI fails if trip status convergence logic regresses.

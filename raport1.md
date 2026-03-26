# Audit: Local Storage vs Firebase Data Flow Architecture
## SOUIGAT Conductor Mobile App

**Date**: March 26, 2026  
**Scope**: Conductor Android mobile app (Kotlin/Room/Firebase) local-first design  
**Auditor**: Code-based analysis of production patterns  

---

## 1. Executive Summary

The SOUIGAT conductor app implements a **hybrid local-first + asynchronous Firebase mirror** architecture with significant inconsistencies and risks:

### Key Findings

- **Blurred source-of-truth boundaries**: Three separate data sources (Room DB, Firestore mirror, Backend API) create divergent truth states without clear hierarchy.
- **Offline completeness** without reconciliation: Mobile can mark operations complete locally/in Firestore while backend DB remains unchanged, causing conductor and office portal view misalignment.
- **Fallback-on-failure pattern**: On any backend error (including permission/state errors), app silently falls back to Firestore writes, hiding real failures from conductor.
- **Trip status treated differently**: Trips use Firestore as read-cache, but status updates use Firestore as fallback sink (never replayed to backend).
- **Tickets/expenses use two-phase sync**: Local writes → immediate sync to backend queue → background worker → Firestore mirror. Trips skip backend entirely on offline.
- **Realtime listeners bypass sync queue**: Firestore listeners directly overwrite local Room data without consistency checks or conflict resolution.
- **Weak conflict resolution**: No timestamp-based conflict detection; Firestore always overwrites local state on listener update.
- **Missing recovery logic**: App restart with pending/syncing items lacks explicit reset or resume strategy.

### Risk Level

**Critical** — Trip completion can be lost or permanently diverged between mobile, backend, and web portals.

---

## 2. Current Source-of-Truth Problems

### Problem 2.1: Trip Status Divergence

**What happens now:**

1. Conductor clicks "Complete Trip" in mobile.
2. App tries backend `/trips/{id}/complete/`.
3. Backend fails (permission denied, invalid status, network timeout).
4. App **silently** marks trip completed locally in Room.
5. App **queues** a Firestore status write.
6. Firestore listener overwrites the queue entry with Firestore's version (if mirror hasn't synced yet).
7. Backend DB remains `in_progress`.
8. Office portal (which reads backend) still shows trip as `in_progress`.
9. Mobile conductor sees `completed`; office staff confused.

**Root cause**: No single source-of-truth. Decision was to treat backend API errors as temporary and fall back to local + mirror. But a 403 (permission) is NOT temporary—it indicates a real state problem on the backend.

**Code refs**:
- `TripRepositoryImpl.completeTrip()` lines 177–215: broad `recoverCatching` block.
- `isRetryableLifecycleError()` lines 244–250: only rejects 401/403/400 if they match specific patterns; 400 is not truly "non-retryable" for trip status.
- `SyncDataWorker.writeTripStatusMirror()` lines ~439–480: writes Firestore status without backend fallback.

---

### Problem 2.2: Tickets/Expenses Dual-Write (but trips skip backend replay)

**What happens now:**

- **Ticket/Expense creation**: Write to Room → Sync queue → Backend API call → success marks item synced → Firestore mirror updated asynchronously.
- **Trip status**: Write to Room → Firestore update attempt → if fails, queue Firestore write again (no backend replay).

**Why it's inconsistent**:

Tickets/expenses **always** go through backend first. Trip status goes to Firestore first (on offline) and **never retries backend**.

**Code refs**:
- `TicketRepositoryImpl.createPassengerTicket()` lines 101–109: enqueues to sync queue.
- `SyncDataWorker.syncItemToFirebase()` lines 179–192: handles "passenger_ticket", "expense" by writing to Firestore after backend sync succeeds.
- `TripRepositoryImpl.queueTripStatusSync()` lines 218–242: only queues Firestore write, no backend endpoint.
- `SyncDataWorker.writeTripStatusMirror()` lines ~439–480: writes to Firestore collection directly (no backend call).

---

### Problem 2.3: Realtime Listeners Bypass Sync Queue

**What happens now:**

1. Firestore listener emits trip list update.
2. `persistTripListLocally()` directly overwrites Room trips table.
3. No conflict detection; no timestamp comparison.
4. If Firestore is stale (mirror write lagged), local becomes stale.
5. If local pending write hasn't synced yet, Firestore listener can wipe it.

**Why it's risky**:

- Realtime listeners are meant for read-cache sync, but they **directly mutate source-of-truth** (Room).
- No idempotency; duplicate listens can overwrite mid-sync tickets.
- Conductor creates offline tickets, but if Firestore listener fires before sync completes, offline ticket data is replaced with remote version (which lacks the local ticket).

**Code refs**:
- `FirebaseTripDataSource.listenTripList()` lines 153–186: snapshot listener with direct update callback.
- `TripRepositoryImpl.startRealtimeTripSync()` lines 252–343: registers listeners and calls `persistTripListLocally()` on updates.
- `TripRepositoryImpl.persistTripListLocally()` (not shown, but inferred): upserts trips without conflict resolution.

---

### Problem 2.4: No Explicit Offline Indicator for Conductor

**What happens now:**

App silently uses stale local data or queued mirrors when backend/Firestore fails. Conductor doesn't know if trip status is "confirmed synced" vs "pending offline".

UI shows queued items but not their pending state clearly relative to shown trip status.

---

## 3. Data Categories and Best Source of Truth

| Data Type | Current Practice | Recommended Truth | Reasoning |
|-----------|------------------|------------------|-----------|
| **Trip (metadata, status)** | Backend DB + Firestore mirror | **Backend DB** | Trips are created by office staff. Conductor reads them. Status transitions are business rules (scheduled→in_progress→completed). Backend enforces constraints. |
| **Passenger Tickets** | Room (local-first) + Backend queue + Firestore mirror | **Room (local-first)** with backend ACK | Conductor creates offline. Must sync to backend for settlement. Firestore is read-only cache. |
| **Cargo Tickets** | Room (local-first) + Backend queue + Firestore mirror | **Backend** (office creates) | Cargo tickets are created by office staff, not conductors. Mobile reads from Firestore mirror. Should never be created locally by conductor. |
| **Trip Expenses** | Room (local-first) + Backend queue + Firestore mirror | **Room (local-first)** with backend ACK | Conductor creates offline. Must sync for settlement audits. Firestore is cache. |
| **Trip Status** | Room + Firestore mirror (no backend queue) | **Backend DB** with Firestore as async mirror | Status transitions are business-critical. Backend is single truth. Firestore is eventual-consistency cache. |
| **Pricing Config** | Room + Firestore mirror | **Firestore mirror (read-only)** | Set by admins. Conductor reads. No local writes. |
| **User Session** | TokenManager (local) + Firebase Auth | **Firebase Auth + TokenManager** | Local cache of auth state. Firebase is truth source for permissions. |

---

## 4. Recommended Rules for Local vs Firebase Ownership

### Rule 1: Backend DB is Canonical for Trips & Status Transitions

**Rule**: All trip state changes (start, complete, cancel, settlement) must be written to backend first. Firestore is eventual-consistency replica only.

**Implementation**:
- Never fallback trip status to Firestore-only writes on backend error.
- If backend is offline, queue the status change for **backend replay**, not just Firestore.
- Add `trip_status_backend` item type to sync queue to distinguish backend-bound status replays from Firestore-only mirrors.

**Enforcement**: `SyncDataWorker` must route `trip_status_backend` to backend API endpoint before marking synced.

---

### Rule 2: Local Room is Canonical for Conductor-Created Items (Tickets, Expenses)

**Rule**: Tickets and expenses created by conductor are authoritative in local Room. Backend sync is required for persistence, Firestore is cache.

**Implementation**:
- Never fetch conductor-created tickets from Firestore as source-of-truth.
- Always check Room first for offline-created tickets.
- Sync queue is the authoritative pending-write journal.
- Firestore listener updates must be merged, not replaced, to avoid losing unsync'd tickets.

**Enforcement**: Repository merge logic must prioritize Room over Firestore when record timestamp or idempotency key matches.

---

### Rule 3: Realtime Listeners Are Read-Cache Only

**Rule**: Firestore snapshot listeners update Room as a read-cache, with conflict resolution based on timestamps and idempotency.

**Implementation**:
- Listeners never overwrite Room records without checking:
  1. Timestamp: If Firestore is older than Room's `updated_at`, skip update.
  2. Idempotency: If Room has pending item with same key, don't overwrite until synced.
  3. Status: Listener updates only affect fully-synced items (Room.status = "active" + has serverId).

**Enforcement**: Add `isSaferToUpdateFromListener()` check in `persistTripListLocally()`.

---

### Rule 4: Sync Queue is the Authoritative Pending Journal

**Rule**: All pending local writes must pass through sync queue. Sync queue state is the single pending-write truth.

**Implementation**:
- Never directly write to Firestore from UI layer.
- All writes → Room + Sync Queue entry.
- Worker processes queue → backend/Firestore in order.
- UI can mark items "pending sync" by observing sync queue status.

**Enforcement**: UI must display sync status from `SyncQueueDao.observePendingCount()`, not inferred from Room timestamps.

---

### Rule 5: Conflict Resolution by Timestamp + Role

**Rule**: When mirror (Firestore) and local (Room) diverge, apply Last-Write-Wins (LWW) by `source_updated_at` timestamp AND role (backend > conductor writes).

**Implementation**:
- Firestore mirror includes `source_updated_at` field (set server-side when mirrored).
- Room includes `updated_at` (local write timestamp).
- On merge: if Firestore source_updated_at > Room updated_at, update. Else keep Room.
- For trip status: always use backend as authority (ignore mirror timestamp comparison).

**Enforcement**: Add timestamp comparison before updating Room from listener or fetch result.

---

### Rule 6: Soft Failures Must Be Visible

**Rule**: When sync fails for a user-facing operation (start trip, complete trip), surface the error to conductor, don't silently fall back to Firestore-only writes.

**Implementation**:
- Distinguish retryable errors (network, 5xx) from terminal errors (403, 400, 401).
- For retryable: queue for retry, show "pending" UI state.
- For terminal: show error dialog, don't complete locally.
- Conductor can retry or escalate to office staff.

**Enforcement**: `isRetryableLifecycleError()` must return false for 400/403/401 when these indicate real permission/state failures (not validation).

---

## 5. Risks in the Current Implementation

### Risk 5.1: Trip Completion Can Be Lost [CRITICAL]

**Scenario**:
1. Conductor offline, completes trip in mobile.
2. Local Room marks trip "completed".
3. Firestore write queued.
4. Conductor regains internet, worker runs.
5. Worker writes to Firestore mirror.
6. Office staff refreshes portal, still sees trip "in_progress" (backend DB unchanged).
7. Settlement doesn't trigger (backend checks trip status).

**Impact**: Revenue recognition delayed or lost; incorrect trip tallies.

**File**: `TripRepositoryImpl.kt` lines 177–215.

---

### Risk 5.2: Fallback Hides Real Permission Errors [HIGH]

**Scenario**:
1. Conductor tries to complete trip but is not assigned (403 Forbidden).
2. App falls back to Firestore write attempt.
3. Firestore rules reject it (Firestore rules require conductor_id match).
4. Both fail, but app returns "success" to UI.
5. Conductor thinks trip is done; office staff confused.

**Impact**: Data inconsistency, confusion, audit trail gaps.

**File**: `TripRepositoryImpl.completeTrip()` lines 183–215; `isRetryableLifecycleError()` lines 244–250.

---

### Risk 5.3: Realtime Listener Overwrites Unsync'd Local Data [HIGH]

**Scenario**:
1. Conductor offline, creates 3 passengers tickets locally.
2. Tickets in Room, queued for sync.
3. Conductor goes online, worker starts syncing tickets to backend.
4. At same time, Firestore listener fires (from office creating trip).
5. Listener calls `persistTripListLocally()`, which overwrites tickets without checking if they're unsync'd.
6. Offline-created tickets disappear.

**Impact**: Data loss of local-only records during sync window.

**File**: `TripRepositoryImpl.startRealtimeTripSync()` lines 277–281; `persistTripListLocally()` (implementation unclear from grep, but logic pattern is dangerous).

---

### Risk 5.4: No Recovery Strategy on App Restart [MEDIUM]

**Scenario**:
1. Worker is mid-sync (items marked SYNCING).
2. App crashes.
3. Room has items in SYNCING state.
4. Worker restarts on next app launch.
5. `resetStuckSyncing()` resets them to PENDING (line 79 in SyncDataWorker), but no logging or audit trail.

**Impact**: Silent retry of mid-flight operations; possible duplicate writes.

**File**: `SyncDataWorker.resetStuckSyncing()` usage line 79.

---

### Risk 5.5: Trip Status Not Idempotent [MEDIUM]

**Scenario**:
1. Conductor completes trip twice (network glitch, user taps twice).
2. First write queues: `trip_status_scheduled→completed`.
3. Second write queues: `trip_status_in_progress→completed` (status already changed in Room after first write).
4. First worker run: Firestore write succeeds.
5. Second worker run: Firestore write fails with invalid transition, item quarantined.
6. Backend still in_progress (never got sync).

**Impact**: Partial state sync; backend divergence.

**File**: `queueTripStatusSync()` lines 218–242 doesn't enforce idempotency per trip+target-status.

---

### Risk 5.6: Firestore Rules Don't Match Backend Rules [MEDIUM]

**Scenario**:
1. Backend enforces: only assigned conductor can complete trip.
2. Firestore rules enforce: conductor_id claim must match trip conductor_id.
3. Conductor's token expires; Firebase custom token issued with old user_id.
4. Conductor tries to complete; backend 401, Firestore write succeeds.
5. Trip is marked completed in mirror but backend knows conductor not authenticated.

**Impact**: Security boundary crossed; audit trail diverged.

**File**: `firestore.rules` lines 96–111 vs `TripViewSet.complete()` in backend (not shown, but inferred).

---

### Risk 5.7: Sync Queue Idempotency Key Not Always Used [LOW]

**Scenario**:
1. Ticket creation with idempotencyKey.
2. Sync queue has unique index on idempotencyKey.
3. Trip status sync uses `trip-status-$tripId-$status-$transitionTime-$eventId`.
4. Two rapid completions with different eventIds = two queue entries (NOT deduplicated).

**Impact**: Duplicate attempts to complete same trip in Firestore.

**File**: `queueTripStatusSync()` lines 226–233 uses unique key, but eventId makes it non-idempotent across retries.

---

## 6. Exact Code Areas to Change

### Change 6.1: Restrict Trip Status Fallback

**File**: `mobile/app/src/main/java/com/souigat/mobile/data/repository/TripRepositoryImpl.kt`

**Method**: `startTrip()` (line 145) and `completeTrip()` (line 177)

**Current**:
```kotlin
.recoverCatching { error ->
    if (!isRetryableLifecycleError(error)) {
        throw error
    }
    // ... fallback to Firestore
    updateLocalTripStatus(id, newStatus)
    return@recoverCatching TripStatusDto(status = newStatus)
}
```

**Change to**:
```kotlin
.recoverCatching { error ->
    if (!isRetryableLifecycleError(error)) {
        throw error
    }
    // Queue backend replay, not Firestore-only write
    queueTripStatusSync(targetTripId, newStatus, System.currentTimeMillis())
    // Do NOT update local status yet; let worker confirm backend ACK
    throw error // Re-throw so conductor knows operation pending
}
```

**Rationale**: Trip status must be confirmed by backend before marking complete locally.

---

### Change 6.2: Add Backend-Replay Queue Type

**File**: `mobile/app/src/main/java/com/souigat/mobile/data/local/entity/SyncQueueEntity.kt`

**Field comment**: `itemType` (line 33)

**Current**:
```kotlin
val itemType: String, // passenger_ticket | cargo_ticket | expense | trip_status
```

**Change to**:
```kotlin
val itemType: String, // passenger_ticket | cargo_ticket | expense | trip_status | trip_status_backend
```

**Rationale**: Distinguish Firestore-only mirror writes from backend-bound status replays.

---

### Change 6.3: Route trip_status_backend to Backend API

**File**: `mobile/app/src/main/java/com/souigat/mobile/worker/SyncDataWorker.kt`

**Method**: `syncItemToFirebase()` (line 169)

**Current**:
```kotlin
when (item.itemType) {
    "trip_status" -> {
        writeTripStatusMirror(item, payload, tripScope)
        ItemSyncResult(action = SyncAction.SYNCED)
    }
    // ...
}
```

**Change to**:
```kotlin
when (item.itemType) {
    "trip_status_backend" -> {
        replayTripStatusToBackend(item, payload, tripScope)
        ItemSyncResult(action = SyncAction.SYNCED)
    }
    "trip_status" -> {
        writeTripStatusMirror(item, payload, tripScope)
        ItemSyncResult(action = SyncAction.SYNCED)
    }
    // ...
}
```

**New method**:
```kotlin
private suspend fun replayTripStatusToBackend(
    item: SyncQueueEntity,
    payload: JSONObject,
    tripScope: TripScope,
): Unit {
    val status = payload.optString("status", "in_progress")
    val tripId = item.tripId
    
    // Inject TripApi and call backend endpoint
    val result = when (status) {
        "in_progress" -> tripApi.startTrip(tripId)
        "completed" -> tripApi.completeTrip(tripId)
        else -> throw IllegalArgumentException("Invalid trip status: $status")
    }
    
    if (!result.isSuccessful) {
        throw Exception("Backend replay failed: ${result.code()}")
    }
    
    // After backend success, write to Firestore
    writeTripStatusMirror(item, payload, tripScope)
}
```

**Rationale**: Backend is source-of-truth; mirror write is secondary.

---

### Change 6.4: Add Timestamp-Aware Merge for Realtime Listeners

**File**: `mobile/app/src/main/java/com/souigat/mobile/data/repository/TripRepositoryImpl.kt`

**Method**: `startRealtimeTripSync()` (line 262) and `persistTripListLocally()` (inferred)

**Current**:
```kotlin
realtimeScope.launch {
    persistTripListLocally(trips)
}
```

**Change to**:
```kotlin
realtimeScope.launch {
    persistTripListLocallySafe(trips)
}
```

**New method**:
```kotlin
private suspend fun persistTripListLocallySafe(trips: List<TripListDto>) {
    for (trip in trips) {
        val localTrip = tripDao.getByLocalOrServerId(trip.id)
        
        // Skip listener update if local is newer (unsync'd local write)
        if (localTrip != null && localTrip.updatedAt > System.currentTimeMillis() - 5000) {
            Timber.d("Skipping stale Firestore update for trip %d; local is newer.", trip.id)
            continue
        }
        
        // Safe to update from Firestore
        persistTripListLocally(listOf(trip))
    }
}
```

**Rationale**: Prevent listener overwrites of pending local writes.

---

### Change 6.5: Enforce Retryable Error Classification

**File**: `mobile/app/src/main/java/com/souigat/mobile/data/repository/TripRepositoryImpl.kt`

**Method**: `isRetryableLifecycleError()` (line 244)

**Current**:
```kotlin
private fun isRetryableLifecycleError(error: Throwable): Boolean {
    return when (error) {
        TripException.NetworkUnavailable -> true
        is TripException.ServerError -> error.code >= 500 || error.code == 408 || error.code == 429
        else -> false
    }
}
```

**Change to**:
```kotlin
private fun isRetryableLifecycleError(error: Throwable): Boolean {
    return when (error) {
        TripException.NetworkUnavailable -> true
        TripException.Unauthenticated -> false  // Terminal: session expired
        TripException.NotAssigned -> false      // Terminal: not conductor
        is TripException.InvalidStatus -> false // Terminal: invalid state transition
        is TripException.ServerError -> 
            (error.code >= 500) || (error.code == 408) || (error.code == 429)
        else -> false
    }
}
```

**Rationale**: Explicitly mark permission/state errors as non-retryable.

---

### Change 6.6: Trigger Immediate Sync for Trip Status

**File**: `mobile/app/src/main/java/com/souigat/mobile/data/repository/TripRepositoryImpl.kt`

**Method**: `queueTripStatusSync()` (line 218)

**Current**:
```kotlin
private suspend fun queueTripStatusSync(tripServerId: Long, status: String, transitionAtMillis: Long) {
    // ... enqueue ...
    if (queued <= 0L) {
        Timber.w("...")
    } else {
        Timber.i("...")
        // NO CALL TO syncScheduler.triggerOneTimeSync()
    }
}
```

**Change to**:
```kotlin
private suspend fun queueTripStatusSync(tripServerId: Long, status: String, transitionAtMillis: Long) {
    // ... enqueue ...
    if (queued <= 0L) {
        Timber.w("...")
    } else {
        Timber.i("...")
        syncScheduler.triggerOneTimeSync()  // Trigger immediate sync (same as tickets)
    }
}
```

**Rationale**: Trip status sync should not wait for 15-min periodic cycle.

---

### Change 6.7: Log Stuck Syncing Reset

**File**: `mobile/app/src/main/java/com/souigat/mobile/worker/SyncDataWorker.kt`

**Method**: `doWork()` (line 66)

**Current**:
```kotlin
override suspend fun doWork(): Result {
    // ...
    syncQueueDao.resetStuckSyncing()
    // No logging
}
```

**Change to**:
```kotlin
override suspend fun doWork(): Result {
    // ...
    val resetCount = syncQueueDao.resetStuckSyncing()
    if (resetCount > 0) {
        Timber.w("SyncDataWorker: reset %d stuck SYNCING items to PENDING.", resetCount)
    }
}
```

**Add to DAO**:
```kotlin
@Query("UPDATE sync_queue SET status = 'PENDING' WHERE status = 'SYNCING' AND syncedAt IS NULL")
suspend fun resetStuckSyncing(): Int  // Return count of rows updated
```

**Rationale**: Audit trail for silent retry logic.

---

## 7. Final Recommended Architecture

### Architecture Diagram (Text)

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Conductor Mobile UI                          │
└──────────────────────────────────────────────────────────────────────┘
                                  │
                ┌─────────────────┼────────────────┐
                │                 │                │
         ┌──────▼─────┐    ┌─────▼────┐    ┌─────▼────┐
         │  Trip List │    │Trip Detail│    │ Dashboard│
         └──────┬─────┘    └─────┬────┘    └─────┬────┘
                │                 │               │
                └─────────────────┼───────────────┘
                                  │
                        ┌─────────▼────────┐
                        │ Repository Layer │
                        │ (Offline-first)  │
                        └─────────┬────────┘
                                  │
        ┌─────────────────────────┼──────────────────────────┐
        │                         │                          │
   ┌────▼─────────┐    ┌─────────▼──────────┐   ┌──────────▼──────┐
   │   Room DB    │    │   Sync Queue       │   │ Firebase Session │
   │ (source of   │    │   (pending journal)│   │ Manager          │
   │  truth for   │    │                    │   └──────────┬──────┘
   │  local work) │    └─────────┬──────────┘              │
   └────┬─────────┘              │                         │
        │                        │                         │
        │        ┌───────────────┼─────────────────────────┼───────────┐
        │        │               │                         │           │
        │        │         ┌─────▼──────────────┐    ┌────▼────┐  ┌───▼────┐
        │        │         │ SyncDataWorker     │    │ Backend  │  │Firebase │
        │        │         │ (async batch)      │    │ API      │  │Firestore│
        │        │         │                    │    │ (Truth)  │  │ (Cache) │
        │        │         │ For trip_status_   │    │          │  │         │
        │        │         │ backend: replay    │    │          │  │         │
        │        │         │ to backend first,  │    │          │  │         │
        │        │         │ then Firestore     │    │          │  │         │
        │        │         │                    │    │          │  │         │
        │        │         │ For trips/tickets: │    │          │  │         │
        │        │         │ Firestore mirror   │    │          │  │         │
        │        │         │ (read-cache only)  │    │          │  │         │
        │        │         └────────────────────┘    │          │  │         │
        │        │                                   └────────┬─┴──┴────┬────┘
        │        │                                          │        │
        │   ┌────▼───────────────┐                 Backend DB    Firestore
        │   │Realtime Listeners   │               (single truth)  (eventual
        │   │(Firestore snapshots)│                             consistency)
        │   │                     │
        │   │ With safeguards:    │
        │   │- Timestamp check    │
        │   │- Idempotency check  │
        │   │- Skip if local new  │
        │   └─────────────────────┘
        │
        └─ (populate Room,
           respect sync queue
           pending status)
```

### Architecture Principles

1. **Room is Local Authority**: Conductor-created data (tickets, expenses) is authoritative in Room until synced.
2. **Backend is Trip Authority**: Trip metadata and status transitions are authoritative in backend DB. Mobile reads backend as source, not Firestore.
3. **Firestore is Async Mirror**: All writes are mirrored to Firestore asynchronously for office portal reads. Firestore is cache, not truth.
4. **Sync Queue is Pending Journal**: Ordered list of pending writes with retry logic and idempotency keys.
5. **Realtime Listeners are Cache Refresh**: Listeners update Room as a read-cache, with timestamp/idempotency guards to prevent overwriting unsync'd local work.
6. **No Fallback Without Replay**: If operation fails, don't mark complete locally; queue for retry with backend as primary target.

---

## 8. Priority Fixes

### P0 (Critical — Do First)

| Fix | File | Reason |
|-----|------|--------|
| Restrict trip status fallback | `TripRepositoryImpl.kt` lines 177–215 | Prevent silent backend divergence |
| Add trip_status_backend queue type | `SyncQueueEntity.kt` line 33 | Separate backend replay from Firestore-only writes |
| Route trip_status_backend to backend API | `SyncDataWorker.kt` lines 169–192 | Ensure backend is authoritative source |
| Enforce retryable error classification | `TripRepositoryImpl.kt` lines 244–250 | Hide permission errors from fallback logic |

### P1 (High — Do Soon)

| Fix | File | Reason |
|-----|------|--------|
| Add timestamp-aware merge for listeners | `TripRepositoryImpl.kt` lines 262–343 | Prevent listener overwrites of unsync'd data |
| Trigger immediate sync for trip status | `TripRepositoryImpl.kt` lines 218–242 | Reduce latency of trip completion |
| Log stuck syncing reset | `SyncDataWorker.kt` line 79 | Audit trail for silent recovery logic |

### P2 (Medium — Plan Later)

| Fix | File | Reason |
|-----|------|--------|
| Add de-duplication for trip status queue entries | `TripRepositoryImpl.kt` lines 226–233 | Prevent double-completion attempts |
| Implement UI pending-sync indicator | UI layers | Show conductor which operations are pending confirmation |
| Align Firestore rules with backend rules | `firestore.rules` lines 96–111 | Prevent security boundary crossings |

---

## 9. Recovery Procedures

### Recovery 9.1: After App Crash with Pending Syncs

**Current state**: Room has items marked SYNCING; `syncedAt IS NULL`.

**Procedure**:
1. `SyncDataWorker.doWork()` calls `resetStuckSyncing()` (with logging).
2. Items reset to PENDING.
3. Worker re-processes them in next round (exponential backoff).
4. Idempotency keys prevent duplicates at backend.

**Enhancement**: Add Timber log message to conductor diagnostics (upload logs to server for analysis).

---

### Recovery 9.2: After Manual Sync Reset

**If conductor manually triggers sync**:
1. App calls `syncScheduler.triggerOneTimeSync()`.
2. Worker starts, reads PENDING batch.
3. For each item: if older than 7 days without sync, prune (line 89).

**Enhancement**: Add UI confirmation for manual reset; explain which records will be pruned.

---

## 10. Implementation Roadmap

**Phase 1 (Week 1)**: P0 changes — backend replay, error classification, queue type.

**Phase 2 (Week 2)**: P1 changes — listener safeguards, immediate sync trigger.

**Phase 3 (Week 3)**: Testing, validation, rollout.

**Phase 4 (Week 4)**: P2 changes, UI enhancements, documentation.

---

## 11. Assumptions

- Backend API will always be available for trip operations (or return 503/timeout for retryable fallback).
- Firestore rules can be updated to match backend authorization model.
- Conductor devices have local storage capacity for Room DB (current sizing ~50MB for typical usage).
- Sync worker can run in background; no immediate user-facing sync required.
- Office staff use web portal, which reads backend DB (not Firestore directly).

---

## 12. Out of Scope

- Web portal source-of-truth audit (separate engagement).
- Backend API contract versioning.
- Firestore indexes optimization.
- Conductor device offline duration limits.

---

**End of Report**

---

## Appendix: File Change Checklist

- [ ] `TripRepositoryImpl.kt` — Restrict fallback, add backend replay queue, trigger sync
- [ ] `SyncDataWorker.kt` — Add trip_status_backend routing, log recovery
- [ ] `SyncQueueEntity.kt` — Update itemType comments
- [ ] `TripRepositoryImpl.kt` — Add timestamp-aware listener merge
- [ ] `TripRepositoryImpl.kt` — Enforce retryable error classification
- [ ] `firestore.rules` — Verify trip status update rules match backend
- [ ] Test: Trip completion offline + online recovery
- [ ] Test: Fallback behavior for permission errors
- [ ] Test: Listener update doesn't overwrite pending local tickets
- [ ] Test: App restart with stuck syncing items
- [ ] Documentation: Update architecture guide for new team members

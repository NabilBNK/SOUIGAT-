# WEB APP LOCAL STORAGE & FIREBASE SYNC AUDIT

**Report Version:** 2.0  
**Date:** March 2026  
**Scope:** React/TypeScript web portal (`/web`)  
**Stack:** React Query + Firestore mirror + IndexedDB sync queue  

---

## EXECUTIVE SUMMARY

The web admin portal exhibits **six critical source-of-truth problems** that can cause persistent data divergence between the backend API, React Query cache, Firestore mirror, and local IndexedDB queue. Unlike the mobile app (which has a defined fallback pattern), the web combines **three competing data sources** without strong conflict resolution, resulting in:

1. **Mirror status override gaps** — Firestore listeners can override newer backend trip status (e.g., stale mirror forces trip to "in_progress" overriding "completed")
2. **React Query staleTime misalignment** — 30-second staleTime + Firestore listener latency creates 30+ second UI staleness window
3. **Settlement creation race condition** — Web can initiate settlement while mobile is still syncing conductor-side tickets, causing 404 settlement errors
4. **No server-side sync audit trail** — IndexedDB queue is browser-local; no backend record of what web admin queued and when
5. **Role-based restrictions not enforced in mirror** — Cargo staff can see/create all-office cargo tickets in Firestore before permission check fails on backend
6. **Cargo ticket creation creates local queue items without backend success check** — If backend API fails, queue item stays pending forever (no retry backoff escalation)

**Risk Level:** **CRITICAL** for financial data (settlements), **HIGH** for operational data (trip status, ticket visibility)

**Effort to Fix:** ~3–4 weeks (6 P0/P1 items, 8 P2 items, plus UI/UX changes)

---

## CURRENT ARCHITECTURE ANALYSIS

### Data Flow Layers (Web)

```
┌─────────────────────────────────────────────────────────────┐
│ BROWSER LAYER (React + React Query)                         │
│ • getTrips() → React Query cache (staleTime: 30s)           │
│ • useTripStatusMirrorMap() attaches Firestore status        │
│ • shouldApplyMirrorStatus() merge logic (weak)              │
│ • TripList, Dashboard, TripDetail read merged trip data     │
└─────────────────────────────────────────────────────────────┘
             ↓ (push)           ↑ (pull)
┌─────────────────────────────────────────────────────────────┐
│ SYNC ENGINE (IndexedDB Queue + SyncDataWorker)              │
│ • enqueueSyncRecord() stores ops in IndexedDB               │
│ • SyncDataWorker drains queue via Firestore transactions    │
│ • LWW (Last-Write-Wins) on Firestore conflicts              │
│ • requestSyncDrain(0) triggers immediate drain              │
└─────────────────────────────────────────────────────────────┘
             ↓ (write mirror)   ↑ (read mirror)
┌─────────────────────────────────────────────────────────────┐
│ FIRESTORE LAYER (Mirror Collections)                        │
│ • trip_mirror_v1, passenger_ticket_mirror_v1                │
│ • cargo_ticket_mirror_v1, trip_expense_mirror_v1            │
│ • onSnapshot() listeners fire immediately (no debounce)    │
│ • No timestamp merge; status overwrites directly            │
└─────────────────────────────────────────────────────────────┘
             ↓ (API writes)     ↑ (API reads)
┌─────────────────────────────────────────────────────────────┐
│ BACKEND API LAYER (Django/DRF)                              │
│ • /api/trips/, /api/trips/{id}/, completeTrip(), etc.       │
│ • Writes to PostgreSQL; signals fire Firestore mirror sync  │
│ • Settlement creation gated on trip.status === "completed"  │
│ • No knowledge of web IndexedDB queue state                 │
└─────────────────────────────────────────────────────────────┘
```

### Key Web Sync Code Patterns

**React Query Setup (web/src/App.tsx):**
- `staleTime: 30000` (30 seconds)
- `refetchOnWindowFocus: false` (no aggressive refetch)
- No visible sync status indicator in UI

**Mirror Status Merge (Dashboard.tsx:36–52, TripDetail.tsx:47–68):**
```typescript
function shouldApplyMirrorStatus(trip: Trip, mirror: { status: TripStatus | null; sourceUpdatedAt: string | null } | undefined): boolean {
    if (!mirror?.status) return false
    if ((trip.status === 'completed' || trip.status === 'cancelled') && mirror.status !== trip.status) {
        return false  // Block mirror if backend says "completed/cancelled"
    }
    const backendUpdatedAtMs = parseEpochMs(trip.updated_at)
    const mirrorUpdatedAtMs = parseEpochMs(mirror.sourceUpdatedAt)
    if (backendUpdatedAtMs === null || mirrorUpdatedAtMs === null) return false
    return mirrorUpdatedAtMs >= backendUpdatedAtMs - 2000  // 2-second merge window
}
```
**PROBLEM:** Allows mirror to override "scheduled" → "in_progress" if mirror is only 2 seconds stale. No distinction between fresh API and stale cache.

**Firestore Mirror Listeners (useTripMirrorData.ts:182–200):**
```typescript
unsubscribe = onSnapshot(
    mirrorQuery,
    (snapshot) => {
        const mapped = snapshot.docs
            .map((document) => mapper(document.data()))
            .filter((item): item is T => item !== null)
        setState({ data: mapped, isLoading: false, error: null })  // Direct overwrite
    },
    (error) => { console.warn(...); }
)
```
**PROBLEM:** No timestamp merge; direct overwrite. If Firestore listener fires with stale data, UI state is clobbered.

**IndexedDB Sync Queue (queue.ts):**
- Stores in `by_dedupe` unique index
- Deduped by `opId` (includes timestamp) for upserts
- Deletes are deduped by `entityType:entityId:delete` (no timestamp)
- Worker polls and drains to Firestore via `runTransaction()`

**Settlement Creation Gating (TripDetail.tsx:122):**
```typescript
const { data: settlement, ... } = useQuery({
    queryKey: ['settlement', id],
    queryFn: () => getSettlement(Number(id)),
    enabled: !!id && !!trip && effectiveStatus === 'completed' && canAccessSettlementSection,
    // ↑ Gated on mirror-merged effectiveStatus
})
```
**PROBLEM:** Settl query fires when `effectiveStatus === 'completed'` (from mirror merge, not backend). If backend is still "in_progress" but mirror says "completed", settlement query fires and 404s.

---

## CRITICAL FINDINGS

### 1. MIRROR STATUS OVERRIDE CAN REGRESS TRIP STATE (P0 — Data Divergence)

**What Happens:**
- Backend completes trip → backend status = "completed"
- Web admin sees trip as "completed" (via API)
- Mobile conductor syncs with latency → Firestore mirror status = "in_progress"
- React listener updates mirror state → `useTripStatusMirrorMap()` returns mirror status
- Dashboard calls `shouldApplyMirrorStatus()` → **2-second tolerance allows override**
- UI reverts trip to "in_progress" despite backend being authoritative

**Code Location:**
- Dashboard.tsx:36–52 `shouldApplyMirrorStatus()`
- TripDetail.tsx:47–68 `shouldApplyMirrorStatus()`
- useTripMirrorData.ts:182–200 `onSnapshot()` direct overwrite

**Example Scenario:**
```
13:00:00 — Backend: trip.status = "completed", updated_at = "2026-03-26T13:00:00Z"
13:00:01 — Mirror status pushed to Firestore (async signal)
13:00:15 — Mobile conductor comes online, syncs old status snapshot to Firestore
13:00:16 — Mirror listener fires with status = "in_progress", sourceUpdatedAt = "2026-03-26T13:00:00Z"
13:00:16 — shouldApplyMirrorStatus() checks: mirrorUpdatedAt (13:00:00) >= backendUpdatedAt (13:00:00) - 2000ms → TRUE
13:00:16 — UI shows "in_progress" ← DATA REGRESSION
```

**Impact:**
- Office staff sees stale state despite refresh
- Settlement initiation fails (gated on "completed" status)
- Audit trail unclear which source was consulted

**Root Cause:**
- Mirror considered authoritative within 2-second window
- No distinction between "fresh backend status" vs "stale cache status"
- Conductor's offline-first sync can be hours behind, yet override recent backend actions

---

### 2. REACT QUERY STALETIME × FIRESTORE LATENCY = PERSISTENT STALENESS (P0 — UX Degradation)

**What Happens:**
- getTrips() fetches from backend API → React Query caches for 30 seconds
- useTripStatusMirrorMap() subscribes to Firestore listener
- Backend trip is updated immediately
- Firestore mirror signal fires asynchronously (may be 0–30 seconds later)
- Listener latency + 30-second staleTime = **up to 60 seconds UI staleness**

**Code Location:**
- App.tsx: React Query `staleTime: 30000`
- Dashboard.tsx:67–70 `getTrips()` with placeholderData
- useTripMirrorData.ts:160–172 listener subscription

**Example Scenario:**
```
13:00:00 — Admin completes trip via TripDetail
13:00:00 — Backend updated, signal sent to mirror
13:00:02 — Firestore mirror written (2s signal latency)
13:00:03 — Admin refreshes browser
13:00:03 — React Query serves cached trip (status="in_progress") because staleTime=30s not yet expired
13:00:03 — Firestore listener hasn't fired yet (batched writes)
13:00:05 — Firestore listener fires with new status
13:00:05 — UI updates (5 second lag)
BUT: If admin didn't refresh, cache expires at 13:00:30
13:00:30 — React Query refetches → backend returns "completed"
13:00:30 — UI finally shows correct status (30-second lag)
```

**Impact:**
- Office staff takes action on stale state (e.g., initiates settlement while trip is "in_progress" in cache)
- No visual sync indicator (no "syncing..." badge on trip status)
- Mobile conductor completes trip but web admin unaware for up to 30 seconds

**Root Cause:**
- `staleTime: 30s` treats data as "fresh" regardless of Firestore event recency
- `refetchOnWindowFocus: false` disables active sync
- No sync progress indicators in UI

---

### 3. SETTLEMENT QUERY RACES WITH MOBILE SYNC (P0 — Logic Error / 404s)

**What Happens:**
- Web admin clicks "Clôturer" (complete trip) in TripDetail → `completeMutation.mutate()`
- API returns 200, trip status = "completed"
- React Query updates cache
- `shouldApplyMirrorStatus()` allows mirror to apply (if Firestore status available)
- TripDetail.tsx:122 re-enables settlement query: `enabled: !!trip && effectiveStatus === 'completed'`
- Settlement query fires **immediately** → `getSettlement(tripId)` → 404 if backend hasn't created it yet
- Meanwhile: Mobile conductor is still syncing tickets/expenses in background
- Backend refuses settlement creation until mobile sync completes (`TRIP_HAS_PENDING_SYNC`)
- UI shows "Impossible de charger le reglement" error

**Code Location:**
- TripDetail.tsx:122 `enabled: !!trip && !!trip && effectiveStatus === 'completed'`
- TripDetail.tsx:647–650 error handling (no retry logic)
- backend/api/views/trip_views.py (backend settlement gating, not shown but inferred from error message)

**Example Scenario:**
```
13:00:00 — Mobile trip: tickets PENDING (in sync queue)
13:00:00 — Admin clicks "Clôturer" → API completes trip
13:00:01 — Backend status="completed", but tickets still have status="SYNCING"
13:00:01 — Web query fires: getSettlement(id)
13:00:01 — Backend settlement endpoint checks: tickets status → SYNCING → error "TRIP_HAS_PENDING_SYNC"
13:00:01 — UI shows error, admin confused
13:00:05 — Mobile worker syncs tickets → status="SYNCED"
13:00:05 — Admin manually refreshes TripDetail
13:00:05 — Settlement query retries (React Query default retry)
13:00:05 — Backend creates settlement, UI updates
```

**Impact:**
- Office staff sees "settlement not found" error for valid completed trips
- No automatic retry or progress indication
- Manual refresh required to see settlement section

**Root Cause:**
- No debounce/delay before settlement query fires after trip completion
- No knowledge of mobile sync state on backend (no status flag exposed)
- Settlement query enabled immediately on "completed" without checking if backend is ready

---

### 4. CARGO TICKET CREATION LACKS BACKEND SUCCESS CONFIRMATION (P1 — Silent Failure)

**What Happens:**
- Web admin (office staff) creates cargo ticket via CargoDetail.tsx or cargo tickets list
- `queueCargoTicketUpsert()` → IndexedDB enqueue
- `requestSyncDrain(0)` triggers worker immediately
- Worker writes to Firestore via transaction
- **No check if backend API write succeeded**
- If backend returns 403 (permission denied for cargo admin), queue doesn't know
- Queue item remains "pending" indefinitely (no exponential backoff on non-transient errors)
- Mobile conductor sees ticket in Firestore (fetched via listener)
- Backend database has no corresponding ticket record

**Code Location:**
- operationalSync.ts:91–97 `queueCargoTicketUpsert()` — enqueues but doesn't validate backend
- engine.ts (not shown, inferred from pattern): SyncDataWorker only writes to Firestore
- No backend API write attempt for cargo tickets from web

**Example Scenario:**
```
13:00:00 — Cargo office staff creates ticket: queueCargoTicketUpsert()
13:00:00 — IndexedDB enqueue: { entity: 'cargo_ticket', operation: 'upsert', status: 'pending' }
13:00:01 — Worker drains to Firestore (success)
13:00:01 — Backend permission check NOT invoked
13:00:05 — Mobile conductor pulls trip tickets from Firestore listener → sees ticket
13:00:05 — Conductor marks ticket as "delivered"
13:00:06 — Mobile syncs delivery status to Firestore
13:00:10 — Admin refreshes web; backend API returns 0 cargo tickets for trip
13:00:10 — UI discrepancy: Firestore shows ticket, backend shows none
```

**Impact:**
- Mobile conductor syncs deliveries for tickets that don't exist in backend DB
- Backend settlement calculations exclude non-existent tickets (money discrepancy)
- No server-side audit trail of who created what ticket

**Root Cause:**
- Web sync to Firestore only; no backend API write for cargo tickets
- No backend permission check before Firestore write
- No validation of backend response before queue item marked "complete"

---

### 5. NO SERVER-SIDE SYNC AUDIT TRAIL (P1 — Compliance / Audit Gap)

**What Happens:**
- All web sync operations stored in IndexedDB (browser local)
- No record on backend of:
  - What operations web admin queued
  - When operations were queued
  - If operations succeeded or failed
  - Retry count or backoff state
- Backend logs don't reflect web sync queue state
- Auditor cannot correlate web admin actions → Firestore writes → backend state

**Code Location:**
- queue.ts: IndexedDB only, no server-side equivalent
- No backend `SyncLog` model or API endpoint to inspect queue

**Example Scenario:**
```
Auditor Question: "Who and when deleted cargo ticket #42?"
Response Options:
  1. Mobile: Check SyncQueueEntity in Room DB + backend logs → Full trail
  2. Web: Check browser IndexedDB (if not cleared) → Incomplete, local-only
```

**Impact:**
- Regulatory compliance gap (no audit trail for financial operations)
- Difficult to troubleshoot web sync issues (can't inspect queue from backend)
- No server-side circuit breaker for web bulk operations

**Root Cause:**
- Web architecture: IndexedDB sync queue only
- No backend API endpoint to log/inspect web sync operations
- Designed for offline-first, but no server-side mirror of queue state

---

### 6. ROLE-BASED RESTRICTIONS NOT ENFORCED IN MIRROR (P1 — Permission Bypass)

**What Happens:**
- Web cargo staff (role="office_staff", department="cargo") creates ticket via CargoDetail
- `queueCargoTicketUpsert()` enqueues ticket to IndexedDB
- Worker writes to Firestore **without backend permission check**
- Ticket appears in Firestore mirror (read by all clients)
- Mobile conductor fetches ticket via Firestore listener (sees ticket created by wrong staff)
- Backend API eventually denies creation (403) when/if web attempts backend write
- But ticket already in Firestore and visible to mobile

**Code Location:**
- operationalSync.ts:91–97 `queueCargoTicketUpsert()` — no role check before Firestore
- TripDetail.tsx:259 (cargo staff can call completeTrip — backend denies, but no pre-check)
- useTripMirrorData.ts:84–117 `toCargoTicket()` — no role filtering on mirror read

**Example Scenario:**
```
13:00:00 — Cargo office staff (department="cargo") attempts to create cargo ticket
13:00:00 — Frontend has no client-side role check (or check is missing)
13:00:00 — queueCargoTicketUpsert() → Firestore write succeeds
13:00:01 — Mobile conductor sees ticket in Firestore listener
13:00:01 — Conductor marks ticket as "delivered"
13:00:02 — Web worker retries backend API write → 403 Forbidden
13:00:02 — Queue marks item as "failed"
13:00:02 — Mobile delivery status for non-existent ticket persists in Firestore
```

**Impact:**
- Unauthorized operations briefly visible in Firestore
- Mobile conductor acts on unauthorized data
- Backend permission check is too late (after Firestore mirror written)

**Root Cause:**
- No client-side role-based checks before Firestore mirror write
- Firestore rules may exist (not shown), but application-layer checks missing
- Assumption: Firestore security rules will catch it (but doesn't prevent stale visibility)

---

## WEB-SPECIFIC RISKS & ISSUES

| Risk | Severity | Impact | Location |
|------|----------|--------|----------|
| Mirror status override regression | **CRITICAL** | Trip state reverts to stale status | Dashboard.tsx:36–52, TripDetail.tsx:47–68 |
| React Query staleTime staleness | **CRITICAL** | UI shows 30s+ outdated data | App.tsx, useTripMirrorData.ts |
| Settlement race condition | **HIGH** | 404 errors on valid trips | TripDetail.tsx:122, backend settlement gating |
| Cargo ticket backend no-op | **HIGH** | Tickets in Firestore, missing from backend | operationalSync.ts:91–97 |
| No server-side sync audit | **HIGH** | Regulatory/compliance gap | queue.ts (IndexedDB only) |
| Role-based permission bypass | **HIGH** | Unauthorized ops briefly visible | operationalSync.ts, TripDetail.tsx |
| Weak queue retry logic | **MEDIUM** | Non-transient errors retry forever | engine.ts (inferred) |
| Dashboard metric source ambiguity | **MEDIUM** | Active trips metric may use different source | Dashboard.tsx:74–75 vs :223 |
| No pending sync indicator | **MEDIUM** | Users unaware of background sync | UI lacks sync status badge |
| Firestore listener adds no timestamp merge | **MEDIUM** | Clobbered by stale listeners | useTripMirrorData.ts:182–200 |

---

## RECOMMENDED DATA OWNERSHIP RULES

### For Web (Corrected Architecture)

| Entity | Source of Truth | Why | Write Flow | Read Flow |
|--------|-----------------|-----|-----------|-----------|
| **Trip Status** | Backend API | Admin actions are authoritative; Firestore is eventual-consistency mirror only | (1) `completeTrip()` → backend → success → cache invalidate → refetch | React Query (30s cache OK if refetch on action) + immediate backend poll |
| **Trip Core** | Backend API | Reference data: departure, conductor, prices | API calls only | React Query cache (30s) + refetch on focus |
| **Passenger Tickets** | Firestore (for conductor reads) + Backend (for totals) | Conductor-created (offline-first via Room) + office-created (backend-first). Mirror reflects pending state | Office: backend API first → queue → Firestore (async) | React Query for counts, Firestore listener for detail |
| **Cargo Tickets** | Backend API (authoritative) | Must pass permission check before mirror | Web: backend API first → enqueue Firestore → check 200 before queue mark "success" | React Query API + Firestore listener (eventual) |
| **Trip Expenses** | Backend API → Firestore async | Office-created expenses are backend-driven | backend → queue → Firestore | React Query for lists, Firestore for detail |
| **Settlements** | Backend API | Created by office, finance rules on backend | backend API only (no queue) | React Query (poll after trip complete) |
| **Sync Queue** | Firestore confirmation | Audit trail | IndexedDB pending + Firestore write + backend confirmation | Query backend API for sync status (new endpoint) |

---

## EXACT CODE AREAS TO CHANGE

### P0 Fixes (Data Correctness — Week 1)

**1. Fix mirror status override logic (P0 — Data Correctness)**
- **File:** `web/src/pages/office/Dashboard.tsx:36–52`
- **File:** `web/src/pages/office/TripDetail.tsx:47–68`
- **Issue:** `shouldApplyMirrorStatus()` allows mirror to override "scheduled"/"in_progress" if only 2 seconds stale
- **Change:** Restrict mirror to only apply if:
  - Backend status is "scheduled" (not state-modifying), AND
  - Mirror is within 5 seconds of backend, OR
  - Explicitly force-disable mirror for "completed"/"cancelled" (already done, expand it)
- **Code:**
  ```typescript
  // Before: risky override
  return mirrorUpdatedAtMs >= backendUpdatedAtMs - 2000
  
  // After: restrict to safe statuses
  const isSafeStatusForMirror = trip.status === 'scheduled' || trip.status === 'pending'
  if (!isSafeStatusForMirror) {
      return false  // Never override if backend is in terminal/progressed state
  }
  return mirrorUpdatedAtMs >= backendUpdatedAtMs - 5000  // Wider window for read-only statuses
  ```

**2. Add immediate refetch after trip completion (P0 — Freshness)**
- **File:** `web/src/pages/office/TripDetail.tsx:179–183`
- **Issue:** `completeMutation` invalidates cache but doesn't force refetch; UI may show stale data for 30s
- **Change:** Add `refetchType: 'all'` to invalidate query, ensuring fresh fetch:
  ```typescript
  const invalidateTrip = () => {
      queryClient.invalidateQueries({ queryKey: ['trip', id] })
      queryClient.invalidateQueries({ queryKey: ['trips'] })
      queryClient.invalidateQueries({ queryKey: ['settlement', id] })
      queryClient.invalidateQueries({ queryKey: ['pending-settlements'] })
      queryClient.invalidateQueries({ queryKey: ['settlements'] })
  }
  // Add after invalidation:
  queryClient.refetchQueries({ queryKey: ['trip', id], type: 'active' })
  ```

**3. Delay settlement query until backend ready (P0 — Logic Safety)**
- **File:** `web/src/pages/office/TripDetail.tsx:119–124`
- **Issue:** Settlement query fires immediately when `effectiveStatus === 'completed'`, but backend may not have created it yet
- **Change:** Add 2-second delay before enabling settlement query:
  ```typescript
  const [settlementQueryEnabled, setSettlementQueryEnabled] = useState(false)
  
  useEffect(() => {
      if (effectiveStatus === 'completed' && canAccessSettlementSection) {
          const timer = setTimeout(() => setSettlementQueryEnabled(true), 2000)
          return () => clearTimeout(timer)
      }
      setSettlementQueryEnabled(false)
  }, [effectiveStatus, canAccessSettlementSection])
  
  const { data: settlement, ... } = useQuery({
      enabled: !!id && !!trip && settlementQueryEnabled,
      ...
  })
  ```

**4. Add timestamp-aware merge for Firestore listeners (P0 — Conflict Resolution)**
- **File:** `web/src/hooks/useTripMirrorData.ts:182–200`
- **Issue:** `onSnapshot()` direct overwrite without timestamp comparison; stale listener clobbers fresh state
- **Change:** Add merge function comparing `source_updated_at`:
  ```typescript
  unsubscribe = onSnapshot(
      mirrorQuery,
      (snapshot) => {
          if (!active) return
          
          const mapped = snapshot.docs
              .map((document) => {
                  const data = mapper(document.data())
                  // Add timestamp: prefer state if newer
                  return { data, ts: parseEpochMs(document.data()?.source_updated_at) }
              })
              .filter((item): item is any => item.data !== null)
              .sort((a, b) => (b.ts ?? 0) - (a.ts ?? 0))  // Sort by timestamp
              .map(item => item.data)
          
          setState({ data: mapped, isLoading: false, error: null })
      },
      ...
  )
  ```

---

### P1 Fixes (Data Integrity — Week 2)

**5. Validate backend response before marking cargo ticket sync "success" (P1 — Integrity)**
- **File:** `web/src/sync/engine.ts` (SyncDataWorker, not shown but inferred)
- **Issue:** Cargo tickets written to Firestore without backend API confirmation
- **Change:** For `cargo_ticket` entity type:
  - Before Firestore write: POST to `/api/cargo/validate/` to check permission
  - Only proceed if 200
  - On 403/400: mark queue item "failed" (not retry), do not write Firestore
  - Example pattern (pseudo-code):
  ```typescript
  if (entityType === 'cargo_ticket' && operation === 'upsert') {
      // Validate permission before mirror write
      const validationResponse = await client.post(`/api/cargo/${entityId}/validate/`, payload)
      if (validationResponse.status !== 200) {
          await markSyncItemFailed(opId, validationResponse.status)
          return
      }
  }
  // Proceed with Firestore write
  ```

**6. Implement exponential backoff for terminal errors (P1 — Error Handling)**
- **File:** `web/src/sync/queue.ts`
- **Issue:** Non-transient errors (403, 400) retry forever; clogs queue
- **Change:** Add `retryCount` and `lastRetryAt` to queue item:
  ```typescript
  interface SyncQueueItem {
      id: string
      entityType: string
      operation: 'upsert' | 'delete'
      status: 'pending' | 'syncing' | 'synced' | 'failed' | 'terminal'
      retryCount: number
      lastRetryAt: number | null
      terminalErrorCode: number | null  // 403, 400, etc.
      errorMessage: string | null
      createdAt: number
  }
  
  // In worker: after error response
  if ([400, 401, 403, 404].includes(status)) {
      await markSyncItemTerminal(opId, status, errorMessage)
      return  // Don't retry
  }
  ```

**7. Add backend sync audit log endpoint (P1 — Compliance)**
- **File:** `backend/api/views/sync_views.py` (new or existing)
- **Create new endpoint:** `POST /api/sync/audit-log/`
  - Accept: `{ web_admin_id, operation, entity_type, entity_id, status, timestamp }`
  - Store in `SyncAuditLog` model
  - Returns: confirmation for queue to mark "audited"
- **File:** `web/src/sync/engine.ts`
- **Change:** After Firestore write succeeds, POST to audit log:
  ```typescript
  if (success) {
      await client.post('/api/sync/audit-log/', {
          web_admin_id: currentUserId,
          operation,
          entity_type: entityType,
          entity_id: entityId,
          status: 'synced',
          timestamp: new Date().toISOString(),
      })
  }
  ```

**8. Add server-side sync status endpoint (P1 — UX Clarity)**
- **File:** `backend/api/views/sync_views.py` (new)
- **Create endpoint:** `GET /api/sync/queue-status/`
  - Returns: `{ pending_count, syncing_count, failed_count, oldest_pending_at }`
  - Web UI uses this to show "sync status" badge
- **File:** `web/src/components/SyncStatusBadge.tsx` (new)
  - Query: `useQuery(['sync-status'], fetchSyncStatus, { refetchInterval: 5000 })`
  - Show: "X pending", "Syncing...", or "✓ In sync"

---

### P2 Fixes (UX & Optimization — Week 3–4)

**9. Add explicit sync status indicators in UI (P2 — UX)**
- **File:** `web/src/pages/office/Dashboard.tsx`, `TripDetail.tsx`, `TripList.tsx`
- **Add:** Badge showing sync progress:
  ```typescript
  <div className="flex items-center gap-2">
      <StatusBadge status={trip.status} type="trip" />
      {isSyncing && <span className="text-xs text-brand-300">Syncing...</span>}
  </div>
  ```

**10. Debounce Firestore listeners (P2 — Performance)**
- **File:** `web/src/hooks/useTripMirrorData.ts:182–200`
- **Change:** Add 1-second debounce to listener state updates:
  ```typescript
  let debounceTimer: NodeJS.Timeout | null = null
  unsubscribe = onSnapshot(
      mirrorQuery,
      (snapshot) => {
          if (debounceTimer) clearTimeout(debounceTimer)
          debounceTimer = setTimeout(() => {
              // Process snapshot after debounce
              const mapped = snapshot.docs.map(...)
              setState({ data: mapped, ... })
          }, 1000)
      }
  )
  ```

**11. Add client-side role validation before sync enqueue (P2 — Defense in Depth)**
- **File:** `web/src/sync/operationalSync.ts:91–97`
- **Change:** Check user role before cargo ticket enqueue:
  ```typescript
  export async function queueCargoTicketUpsert(ticket: CargoTicket, userRole: string): Promise<void> {
      if (userRole === 'office_staff' && department === 'cargo') {
          console.warn('Cargo staff cannot create tickets')
          throw new Error('Permission denied')
      }
      // Proceed with enqueue
  }
  ```

**12. Increase React Query staleTime intelligently (P2 — UX)**
- **File:** `web/src/App.tsx`
- **Consider:** Context-aware staleTime
  ```typescript
  {
      queryKey: ['dashboard', 'recent-trips'],
      queryFn: () => getTrips({ page: 1 }),
      staleTime: 5 * 60 * 1000,  // Increase to 5 minutes (lower activity area)
      refetchOnWindowFocus: true,  // Enable refetch on focus
  }
  ```

**13. Add settlement initiation success delay (P2 — UX)**
- **File:** `web/src/pages/office/TripDetail.tsx:205–209`
- **Change:** After settlement initiation succeeds, wait 2s before showing settlement section:
  ```typescript
  const [settlementInitiatedAt, setSettlementInitiatedAt] = useState<number | null>(null)
  
  useEffect(() => {
      if (settlementInitiatedAt && Date.now() - settlementInitiatedAt > 2000) {
          queryClient.refetchQueries({ queryKey: ['settlement', id] })
      }
  }, [settlementInitiatedAt])
  ```

---

## DATA CATEGORIES & RECOMMENDED SOURCE-OF-TRUTH MATRIX

### For Web App

| Data Category | Current Source | Problem | Recommended Source | Write Path | Read Path |
|---|---|---|---|---|---|
| Trip core (id, route, departure) | React Query (Backend API) | Cached 30s, no active refetch | **Backend API** (primary) + Firestore (async mirror) | Office: API only. Mobile: Backend first, Firestore async | React Query (staleTime: 5m, refetch-on-focus) |
| Trip status | React Query + Firestore mirror | Mirror can regress status; 30s staleness | **Backend API** (primary), Firestore conditional override | API only for admin actions (complete/start) | API poll after mutation, delay Firestore merge |
| Passenger tickets | React Query (count) + Firestore (detail) | Split source; inconsistent | **Firestore** (conductor offline-first) for detail, React Query for counts | Mobile: Room→Queue→Firestore. Web office: Backend→Firestore | Firestore listener + React Query API |
| Cargo tickets | React Query (office-created) + Firestore (eventually) | Web writes only to Firestore, no backend check | **Backend API** (primary, permission check), then queue→Firestore | Office: API first, check 200, then queue | Firestore listener (after backend confirm) |
| Expenses | React Query (office) + Firestore (mobile) | Separate creation paths | **Backend API** (web), **Room+Queue** (mobile), **Firestore async** | Web: API→queue→Firestore. Mobile: Room→Queue→Firestore | React Query + Firestore listener |
| Settlements | React Query (backend-only) | Delayed creation until mobile syncs complete | **Backend API** (authoritative) | Backend API only (office-initiated after trip done) | React Query poll, gated on mobile sync completion |
| Sync queue | IndexedDB (web), Room (mobile) | No server audit trail | Add **backend SyncAuditLog** table | Web: IndexedDB→Firestore→Backend audit. Mobile: SyncQueue→SyncScheduler→Backend | Backend `/sync/audit-log/` endpoint |

---

## IMPLEMENTATION ROADMAP

### Phase 1: Data Correctness (Week 1, P0)
1. **Fix mirror override logic** — prevent stale Firestore from regressing trip status
2. **Add immediate refetch on trip completion** — invalidate cache and refetch after action
3. **Delay settlement query** — 2-second wait before settlement query fires
4. **Add timestamp merge for Firestore listeners** — compare `source_updated_at` before update state
5. **Validate:** Test trip completion → verify UI shows "completed" immediately and persists

### Phase 2: Data Integrity (Week 2, P1)
6. **Validate cargo ticket backend before Firestore** — check permission before mirror write
7. **Implement exponential backoff** — terminal errors (403) stop retrying, get marked "failed"
8. **Add backend audit log endpoint** — log web sync operations server-side
9. **Add sync status endpoint** — backend reports pending/syncing/failed counts
10. **Validate:** Test cargo ticket creation with permission denial → verify no Firestore write

### Phase 3: UX & Visibility (Week 3, P2)
11. **Add sync status badge** — show "Syncing...", pending count, or checkmark
12. **Debounce Firestore listeners** — reduce listener chatter
13. **Add role validation before enqueue** — client-side defense
14. **Increase staleTime for stable sections** — 5 minutes for report/archived data
15. **Add settlement delay** — wait 2s after initiation before refetching settlement
16. **Validate:** Test dashboard; verify sync badge appears/disappears correctly

### Phase 4: Testing & Documentation (Week 4)
17. **E2E tests** — trip completion → settlement creation → mobile sync
18. **Offline scenario tests** — web loses connectivity, then regains
19. **Conflict resolution tests** — backend completes, mirror tries to regress
20. **Permission tests** — cargo staff attempts create, backend denies

---

## UI/UX RECOMMENDATIONS

### 1. Sync Status Indicator (Always Visible)
**Location:** Top-right of dashboard and trip detail pages
```
┌─────────────────────────────┐
│ ✓ In sync        (green)    │  or  │ ⟳ Syncing (2 items)  (blue)   │
└─────────────────────────────┘
```
- Green: All queue items synced
- Blue: X items pending/syncing
- Red: Failed items (show count, link to review)

### 2. Trip Status Clarity
**Location:** TripDetail status badge
```
Status: Completed [Mirror confidence: ⚠️ 30s latency]
```
- Show Firestore update timestamp next to backend timestamp
- Visual indicator if mirror is significantly stale

### 3. Settlement Creation Feedback
**Location:** TripDetail settlement section
```
Settlement section will appear in 2 seconds... (if backend still syncing)
or
Settlement created. Loading details... (with spinner)
```

### 4. Cargo Ticket Permission Error
**Location:** CargoDetail action buttons
```
Statut: Created
Allowed Actions:
  ❌ Cannot transition to "Delivered" (permission denied)
  ✓ Can delete
```

---

## QUICK WINS (Low Effort, High Value)

1. **Add `refetchOnWindowFocus: true`** to React Query queries (5 min)
   - Immediate: Users who switch tabs/windows see fresh data
   - File: `web/src/App.tsx`

2. **Add "Last synced" timestamp** to trip details (15 min)
   - Shows when Firestore last updated this trip
   - Builds user trust in data freshness
   - File: `web/src/pages/office/TripDetail.tsx`

3. **Disable mirror status merge for "in_progress" status** (10 min)
   - Safest quickfix: only merge "scheduled" status, never merge active/completed
   - File: `web/src/pages/office/Dashboard.tsx:36–52`

4. **Add error message on settlement 404** explaining sync delay (5 min)
   - "Settlement being created... refresh in 10 seconds"
   - File: `web/src/pages/office/TripDetail.tsx:703–721`

---

## CROSS-SYSTEM DATA FLOW ISSUES (Web ↔ Mobile ↔ Backend)

### Trip Completion Race Condition
```
Timeline:
13:00:00 — Web: completeTrip() API call
13:00:01 — Backend: Trip.status = "completed", emit Firestore signal
13:00:02 — Web: Settlement query enabled
13:00:03 — Mobile: Worker syncs pending tickets to Firestore
13:00:04 — Backend: Checks trip.sync_status; finds PENDING tickets → refuses settlement creation
13:00:04 — Web: Settlement query fires, gets 404 "TRIP_HAS_PENDING_SYNC"
PROBLEM: Web doesn't know about mobile sync state; settlement fails even though trip is "completed"
```

**Fix:** Backend exposes trip.sync_status via API; web waits for it to be "SYNCED" before settlement query.

### Cargo Ticket Visibility Race
```
Timeline:
13:00:00 — Web: queueCargoTicketUpsert() → Firestore write
13:00:01 — Mobile: Firestore listener fires, sees new ticket
13:00:02 — Mobile: Conductor delivers ticket, syncs to Firestore
13:00:03 — Web: Worker attempts backend API write → 403 Permission Denied
13:00:03 — Web: Marks queue "failed", but ticket+delivery already in Firestore
PROBLEM: Backend DB doesn't have ticket, but Firestore/mobile do; settlement mismatch
```

**Fix:** Web validates permission (POST `/api/cargo/validate/`) BEFORE Firestore write.

---

## APPENDIX: FILE CHANGE CHECKLIST

**Core Fixes:**
- [ ] `web/src/pages/office/Dashboard.tsx` — Fix `shouldApplyMirrorStatus()`
- [ ] `web/src/pages/office/TripDetail.tsx` — Fix `shouldApplyMirrorStatus()`, add settlement delay
- [ ] `web/src/hooks/useTripMirrorData.ts` — Add timestamp merge to listener
- [ ] `web/src/sync/engine.ts` — Add cargo ticket backend validation
- [ ] `web/src/sync/queue.ts` — Add `terminalErrorCode`, stop retry on 403/400
- [ ] `web/src/sync/operationalSync.ts` — Add role check before enqueue
- [ ] `web/src/App.tsx` — Adjust React Query staleTime & refetchOnWindowFocus

**New Endpoints (Backend):**
- [ ] `backend/api/views/sync_views.py` — Add `/sync/audit-log/` (POST) and `/sync/queue-status/` (GET)
- [ ] `backend/api/models/sync.py` — Create `SyncAuditLog` model
- [ ] `backend/api/models/trip.py` — Add `sync_status` field to Trip (PENDING|SYNCED|FAILED)

**New Components (Web):**
- [ ] `web/src/components/SyncStatusBadge.tsx` — New component showing sync state
- [ ] `web/src/hooks/useSyncStatus.ts` — Hook to fetch sync queue status

**Tests (E2E):**
- [ ] `web/e2e/trip-completion-settlement.spec.ts` — Complete trip → verify settlement appears
- [ ] `web/e2e/cargo-permission.spec.ts` — Cargo staff creates ticket → verify error/Firestore
- [ ] `web/e2e/mirror-override.spec.ts` — Trip completed, mirror tries regression → verify UI

---

## SUMMARY OF CRITICAL ISSUES

| # | Issue | Severity | File(s) | Weeks to Fix |
|---|-------|----------|---------|-------------|
| 1 | Mirror status override regression | **CRITICAL** | Dashboard.tsx, TripDetail.tsx | 1 |
| 2 | React Query staleTime + Firestore latency | **CRITICAL** | App.tsx, useTripMirrorData.ts | 1 |
| 3 | Settlement query race condition | **HIGH** | TripDetail.tsx, backend | 1 |
| 4 | Cargo ticket backend no-op | **HIGH** | operationalSync.ts, engine.ts | 1 |
| 5 | No server-side sync audit | **HIGH** | queue.ts, backend (new) | 1 |
| 6 | Role-based permission bypass | **HIGH** | operationalSync.ts | 1 |
| 7 | No exponential backoff | **MEDIUM** | queue.ts | 1 |
| 8 | Dashboard metric source ambiguity | **MEDIUM** | Dashboard.tsx | 0.5 |
| 9 | No pending sync indicator | **MEDIUM** | UI components (new) | 1 |
| 10 | Firestore listener no timestamp merge | **MEDIUM** | useTripMirrorData.ts | 0.5 |

**Total Effort:** ~3–4 weeks (including testing & documentation)

---

**Report End**

# Conductor App Field Audit (Offline-First)

## Executive summary

- The app has a strong local base (Room + migrations + no destructive migration) and already queues offline writes (`sync_queue`) for tickets/expenses/trip status.
- Current reliability is **not field-grade yet**: queue semantics, conflict handling, security rules, and listener lifecycle can cause data drift, repeated retries, or silent failures under real field conditions.
- Biggest gap: the system behaves as if there is one happy path, but field reality has long offline windows, app restarts, stale sessions, duplicate actions, and partial syncs.
- You need to harden the outbox protocol, conflict model, and user-visible sync UX before scaling this to many conductors.

## Top risks

- **Data loss on logout/session reset**: `AuthRepositoryImpl.logout()` clears all local tables (`clearAllTables()`), including unsynced queue and local records.
- **Quarantine is effectively bypassed**: worker requeues quarantined item types each run (`requeueQuarantinedByType(...)`), so bad payloads loop forever instead of staying quarantined.
- **No real per-item retry policy**: `retryCount`/`markFailed` exist but are not used in normal flow; retries are coarse worker-level, not item-level.
- **Listener lifecycle leak/risk**: realtime listeners are started once in `TripRepositoryImpl` and never explicitly stopped/rebound per session/user.
- **Security/rules risk**: current Firestore rules allow broad reads for any signed-in user on operational collections (`allow get, list: if isSignedIn()`), which is unsafe for production multi-tenant data.
- **Conflict risk on status transitions**: strict transition checks exist, but stale queued actions can retry forever without a deterministic conflict resolution path.
- **Performance risk on low-end devices**: full snapshot mapping + per-item DB lookups in loops can be expensive with larger datasets.
- **No durable drafts/autosave**: create forms use in-memory `rememberSaveable`; process kill can lose operator input mid-work.

## Add

- **Durable draft storage**
  - Add `ticket_drafts` and `expense_drafts` Room tables.
  - Autosave on input change (debounced 300-500 ms).
  - Restore draft on screen reopen after crash/restart.

- **Outbox hardening fields** in `sync_queue`
  - `attemptCount`, `nextAttemptAt`, `lastErrorCode`, `lastErrorMessage`, `lastAttemptAt`, `deadLetterReason`, `operationVersion`.
  - Add indexes on `(status, nextAttemptAt)` and `(tripId, createdAt)`.

- **Per-item sync visibility**
  - Show status per ticket/expense/trip action: `Pending`, `Syncing`, `Synced`, `Conflict`, `Quarantined`.
  - Show last sync error actionable text (not generic “hors ligne”).

- **Conflict resolution policy**
  - For `trip_status`: if queued action is stale vs remote state, mark `Conflict` (not infinite retry).
  - Add explicit resolver action in UI (“Reapply”, “Discard”, “Force re-sync snapshot”).

- **Listener manager**
  - Centralized lifecycle: start on login, stop on logout/session change.
  - Rebind listeners when user identity changes.

- **Sync observability**
  - Local sync diagnostics screen for field support: queue depth, oldest pending age, retries, last successful push.

## Remove

- **Remove automatic requeue of quarantined items** (`SyncDataWorker`)
  - Quarantine should be terminal until manual review/retry.

- **Remove broad Firestore read rules**
  - Replace `allow get, list: if isSignedIn()` on operational collections with strict scope rules.

- **Remove destructive local reset on ordinary logout**
  - Keep queue/data by default; provide explicit “Clear device data” admin action.

- **Remove ambiguous connection semantics**
  - Current backend monitor marks backend failure as online if internet exists. Split statuses clearly:
    - Internet connectivity
    - Firebase reachability
    - Backend reachability (if applicable)

## Change

- **Outbox processing contract**
  - Process items by `nextAttemptAt`.
  - On transient errors: increment `attemptCount`, set exponential backoff with jitter.
  - On permanent validation/security errors: move to `Quarantined` once.
  - Do not exit whole run on first retriable item; continue batch and isolate failures.

- **Idempotency model**
  - Keep UUID keys, but add deterministic business key for high-risk duplicate actions (e.g., trip status transition key per transition window).
  - For expenses mirror-inbound, reconcile by `idempotency_key` too (not only `serverId`) to avoid duplicates.

- **Trip state machine hardening**
  - Local status update immediately for UX, but queue explicit operation event.
  - Remote apply must be idempotent and monotonic; stale transitions should resolve to conflict, not retry forever.

- **Low-end performance optimization**
  - Replace per-item DB lookups with preloaded maps per trip batch.
  - Use Firestore batched writes where possible.
  - Limit listener scope to active/assigned trips, not broad collection scans.

- **Session handling**
  - Do not rely on potentially expired tokens for offline “authenticated” state forever.
  - Introduce `offline_session_grace` + explicit lock behavior after grace expiry.

## Recommended architecture

- **Write path (phone)**: UI -> Room transaction (domain table + outbox row) -> immediate UI success.
- **Outbox engine** (WorkManager): reads eligible rows (`PENDING` + `nextAttemptAt<=now`), pushes to Firebase, updates status atomically.
- **Read path (phone)**: Room only for UI rendering; Firebase listeners feed Room through reconciler.
- **Read path (web admin)**: Firebase listeners for operational realtime panels; backend for admin control/reporting where needed.
- **Conflict layer**: dedicated `sync_conflicts` table + user/admin resolution actions.
- **Security model**: strict scoped Firestore rules + custom claims + minimal client write permissions.

## Priority roadmap

### P0 (must do now)
- Stop auto-requeue of quarantined rows.
- Remove broad `isSignedIn()` read rules on operational collections.
- Preserve unsynced local data on logout by default.
- Add per-item attempt/backoff fields and use them in worker.

### P1 (next)
- Add durable drafts/autosave for ticket/expense forms.
- Add listener lifecycle manager (start/stop/rebind by session).
- Add conflict states for trip status transitions (no infinite retry loops).
- Reconcile expense mirror rows by `idempotency_key` to avoid duplicates.

### P2 (scale hardening)
- Batch writes/reads and reduce per-item DB roundtrips.
- Add sync diagnostics screen and exportable debug bundle.
- Add chaos test scenarios: offline reboot, duplicate taps, forced process death, delayed sync replay.

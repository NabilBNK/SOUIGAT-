# PLAN-phase-3-5-sync

## 1. Objective
Implement the offline-first **Synchronization Worker (Phase 3.5)** for the SOUIGAT Mobile application. This worker will systematically pull `PENDING` items generated in Phases 3.3 and 3.4 from the Room `SyncQueueEntity` table and push them to the Django backend.

## 2. Socratic Gate (REQUIRED)
Before any code is written, please resolve the following architectural unknowns:

> **Q1. Endpoint Strategy**: Does Django expose individual endpoints for each ticket/expense (e.g. `POST /api/tickets/passenger/`), or does it have a single bulk-sync ingestion endpoint (e.g. `POST /api/sync/`) that accepts an array of mixed payloads?
> 
> **Q2. Background Execution Rules**: Should `WorkManager` be scheduled as a `PeriodicWorkRequest` (e.g. every 15 minutes) AND optionally triggered by a `OneTimeWorkRequest` when `Constraints.setRequiredNetworkType(NetworkType.CONNECTED)` becomes satisfied?
> 
> **Q3. Token Expiration**: The sync worker executes in the background. If the conductor has been offline for hours and their JWT token expires during a background sync, how should the worker react natively? 
> 
> **Q4. Error State Handling**: If an item throws a `400 Bad Request` or `409 Conflict` (meaning Django rejected the schema, not a network failure), should the item status be changed to `FAILED` (stopping retries) or dropped entirely from the DB?

## 3. High-Level Requirements

### 3.1 Data Layer & Network
- Define Retrofit interfaces matching the payload structures generated in Phase 3.3/3.4.
- Since `idempotencyKey` is now successfully persisted in the JSON payload, ensure the `POST` body transmits it perfectly.

### 3.2 Domain & Worker
- Create `SyncDataWorker` extending `CoroutineWorker`.
- Inject `SyncQueueDao` and the Retrofit `ApiService` via Hilt (`@HiltWorker`).
- Fetch all `SyncStatus.PENDING` items ordered by `createdAt ASC` to maintain chronological consistency.
- Handle Responses:
  - `2xx Success`: Update status to `COMPLETED` (or delete the row).
  - `4xx Client Error` / `5xx Server Error`: Log or mark as `FAILED`.
  - `IOException` (Network Drop): Leave as `PENDING` to allow `WorkManager` standard exponential backoff retries.

### 3.3 Presentation Layer
- Optionally expose a global `SyncStatusState` flow (e.g., in the toolbar) showing "Syncing (3 items)..." or "All changes saved".
- Give conductors a manual "Force Sync" button inside `TripDetailScreen`.

### 4. Quality & Verification Process
- Mock an HTTP response to test `CoroutineWorker` isolated state transitions (`PENDING` -> `COMPLETED`).
- Ensure memory leaks are avoided (no tight `while(true)` loops, relying solely on WorkManager).

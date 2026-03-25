# Firebase Shared-Layer Integration

This repository now keeps the existing backend database as the source of truth and mirrors selected operational data to Firestore.

## Scope (v1)

- Synced entities:
  - `Trip`
  - `PassengerTicket`
  - `CargoTicket`
  - `TripExpense`
  - `Settlement` (summary)
- Firestore collections:
  - `trip_mirror_v1/{tripId}`
  - `passenger_ticket_mirror_v1/{ticketId}`
  - `cargo_ticket_mirror_v1/{ticketId}`
  - `trip_expense_mirror_v1/{expenseId}`
  - `settlement_mirror_v1/{settlementId}`
- Write sources:
  - backend signal-driven mirror queue (authoritative)
  - web sync engine for operational writes (trip/ticket/cargo/expense/settlement), non-blocking
- Read source: mobile app (Firestore-first for trips, backend fallback)

## Local-First Flow

1. User action is saved to local backend (`/api/...`) first.
2. Backend enqueues a Firebase mirror event on transaction commit.
3. Celery task processes mirror events with retry/backoff and conflict handling.
4. Web can enqueue additional operational mirror jobs (IndexedDB queue) without blocking UI.
5. Mobile reads mirrored data from Firestore and persists to Room.

## Backend Mirror Queue

- Table: `firebase_mirror_events`
- States: `pending`, `in_progress`, `synced`, `failed`, `conflict`
- Deduplication: unique `op_id` per entity operation + source timestamp.
- Retry model: exponential backoff + jitter, bounded by `max_attempts`.
- Periodic sweeper task: `drain_firebase_mirror_events` (every minute).

## Firestore Document Shape

- Shared metadata for all mirrored entities:
  - `source_created_at`, `source_updated_at`, `mirrored_at`
  - `is_deleted`, `deleted_at`
  - `last_op_id`, `sync_version`
- Trip docs include route/bus/conductor and pricing snapshot fields.
- Non-trip docs include `trip_id`; read scope is derived via parent trip mirror document.

## Conflict Resolution

- Last-write-wins based on `source_updated_at`
- Incoming stale operations are marked as `conflict` in local sync queue
- Duplicate operation replay is skipped via `last_op_id`

## Retry/Failure Strategy

- Exponential backoff with jitter
- Retryable Firebase errors are retried automatically (Celery task + persisted next retry)
- Non-retryable errors are marked terminal `failed`
- Queue states persisted in DB: `pending`, `in_progress`, `synced`, `failed`, `conflict`

## Security Model

- Firebase custom token minted by backend endpoint: `POST /api/auth/firebase-token/`
- Custom claims include `role`, `user_id`, `office_id`, `department`, `sync_writer`
- Firestore rules:
  - trip reads scoped by role/office/conductor
  - non-trip reads scoped via linked parent trip
  - writes restricted to `sync_writer == true`

## Backfill Existing Local DB

- Management command:
  - `python manage.py backfill_firebase_mirror`
- Options:
  - `--entity {all,trip,passenger_ticket,cargo_ticket,trip_expense,settlement}`
  - `--batch-size <int>`
  - `--limit <int>`
  - `--enqueue-only`
- Safe to rerun (idempotent via `op_id` + source timestamp).

## Files Added for Firebase

- `firebase.json`
- `firestore.rules`
- `firestore.indexes.json`

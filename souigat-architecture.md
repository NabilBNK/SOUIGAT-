# SOUIGAT — Full End-to-End Architecture Plan (v3.1 — Final)

> **Revision 3.1** — Feb 13, 2026 — Three rounds of critical review (31 items total). Production-ready.

## Goal

Build a dual-platform financial management system (Android + Web) for an intercity bus & cargo company. Backend-first approach. Offline-capable mobile with conflict quarantine. Strict role-based access with office-scoped data isolation.

## Project Type

**FULL STACK**: Backend (Django REST) + Web (React + Vite) + Mobile (Kotlin Android)

---

## Tech Stack

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| **Backend** | Django 5.x + DRF 3.15 | Built-in admin, ORM, security, `Decimal` for money |
| **Database** | PostgreSQL 16 | ACID, JSONB for audit, partitioning support |
| **Web Frontend** | React 19 + Vite 6 + TypeScript | Type safety, data-table ecosystem |
| **Mobile** | Kotlin + Room + Retrofit + WorkManager | Native Android, offline-first |
| **Auth** | JWT (simplejwt) | 15min access, 7d refresh, rotation for web only |
| **Task Queue** | Celery + Redis | Async Excel export (with safety limits) |
| **Cache** | Django cache (Redis) | Pricing config, JWT blacklist |
| **Dev Env** | Docker Compose | PostgreSQL + Redis + Django + Celery |

---

## Success Criteria

- [ ] Conductor creates tickets offline → sync without collisions or data loss
- [ ] Rejected syncs quarantined for admin review (never lost)
- [ ] Office staff sees only their office's data
- [ ] `department=cargo` users manage only cargo tickets
- [ ] Admin sees all offices with immutable audit trail
- [ ] Financial reports match manual calculations (DZD integer + currency metadata)
- [ ] No ticket edits during in-progress trips (price freeze rule)
- [ ] Cargo status reversible by admin with reason + audit
- [ ] Excel export completes within 10 min for 100K records without crashing server!

---

## Authorization Model

### Design: Role + Department

```
user.role       → WHAT you can do (actions)
user.office_id  → WHAT DATA you can see (scope)
user.department → WHICH SUBSET of data you work with (filter)
```

### Roles (4 total)

| Role | Description | Department Options |
|------|-------------|-------------------|
| `admin` | Full access, all offices | `null` (ignored) |
| `office_staff` | Office operations | `all`, `cargo`, `passenger` |
| `conductor` | Trip operations, ticket creation | `null` |
| `driver` | View-only (future) | `null` |

### Permission Matrix

| Permission | admin | office_staff (all) | office_staff (cargo) | conductor |
|-----------|-------|-------------------|---------------------|-----------|
| **Trips** |
| Create trip | ✅ | ✅ | ❌ | ❌ |
| View trips | ✅ | ✅ | ✅ (cargo data only) | ✅ (own) |
| Start/complete trip | ✅ | ❌ | ❌ | ✅ (own) |
| Cancel trip | ✅ | ✅ | ❌ | ❌ |
| **Passenger Tickets** |
| Create | ✅ | ✅ | ❌ | ✅ (in-progress trip) |
| View | ✅ | ✅ | ❌ | ✅ (own trip) |
| Edit | ✅ (completed trips only) | ✅ (completed) | ❌ | ❌ |
| **Cargo Tickets** |
| Create | ✅ | ✅ | ✅ | ✅ (in-progress trip) |
| View | ✅ | ✅ | ✅ (own office) | ✅ (own trip) |
| Receive (pay-on-delivery) | ✅ | ✅ | ✅ | ❌ |
| Edit | ✅ (completed) | ✅ (completed) | ✅ (completed, own office) | ❌ |
| Override status | ✅ (any→any, with reason) | ❌ | ❌ | ❌ |
| **Expenses** |
| Create | ✅ | ❌ | ❌ | ✅ (in-progress trip) |
| View | ✅ | ✅ | ❌ | ✅ (own trip) |
| **Reports** |
| Office reports | ✅ | ✅ | ❌ | ❌ |
| Cross-office | ✅ | ❌ | ❌ | ❌ |
| Export Excel | ✅ | ✅ | ❌ | ❌ |
| **Admin** |
| Manage users/buses/pricing | ✅ | ❌ | ❌ | ❌ |
| View audit log | ✅ | ❌ | ❌ | ❌ |
| Review quarantined syncs | ✅ | ✅ | ❌ | ❌ |

### RBAC: DRF Permission Classes (NOT middleware)

```python
class RBACPermission(permissions.BasePermission):
    """Enforced at ViewSet level — cannot be bypassed by forgetting a decorator"""
    def has_permission(self, request, view):
        required = getattr(view, 'required_permission', None)
        user_perms = ROLE_PERMISSIONS[request.user.role]
        if user.department == 'cargo':
            user_perms = CARGO_DEPT_PERMISSIONS
        return '*' in user_perms or required in user_perms

class TripStatusPermission(permissions.BasePermission):
    """Ticket edits ONLY on completed/cancelled trips"""
    def has_object_permission(self, request, view, obj):
        if request.method in ('PATCH', 'PUT', 'DELETE'):
            if obj.trip.status in ('scheduled', 'in_progress'):
                raise PermissionDenied("Cannot edit tickets during active trips")
        return True
```

### Django Admin Security (Production)

```python
# settings_production.py
ADMIN_ENABLED = False  # Disable /admin/ in production

# OR restrict to IP whitelist + superuser only:
ADMIN_ALLOWED_IPS = ['10.0.0.1']  # Office network only
# Custom AdminSite that checks IP + enforces 2FA
```

---

## Business Rule: Trip Price Freeze

```
pricing_config ──[read once at trip creation]──► trips.price_snapshot
                                                    │
                                             [frozen, immutable]
                                                    │
                             trip.status = 'in_progress'
                                    │               │
                              Conductor CAN:    Admin CANNOT:
                              create tickets    edit tickets
                                    │
                             trip.status = 'completed'
                                    │               │
                              Conductor CANNOT:  Admin CAN:
                              create tickets     edit (audited)
```

---

## Critical: Sync Architecture (v3.1 — Quarantine + Row Locking)

### Problem Solved

Network partitions, battery death, stale client state, and human error (double-assigned trips) can all cause sync failures. **No ticket data should EVER be lost.**

### Deterministic Idempotency Keys (Immutable Fields Only)

```kotlin
// Mobile: content-based hash using IMMUTABLE fields
// NEVER use ticket_number (resets on app reinstall)
fun generateIdempotencyKey(tickets: List<Ticket>): String {
    val content = tickets.sortedBy { it.createdAtEpochMs }
        .joinToString("|") { ticket ->
            listOf(
                ticket.tripId,
                ticket.passengerName ?: "ANON",
                ticket.origin,
                ticket.destination,
                ticket.price,
                ticket.createdAtEpochMs  // Stable across reinstalls
            ).joinToString(":")
        }
    return "sync-${userId}-${sha256(content).take(16)}"
}
// Same passengers + same prices + same timestamps = same key
// Battery dies mid-sync → retry → same key → "already_processed"
// App reinstall → ticket_numbers may change, but hash stays same
```

### Quarantine with Row Locking (No Race Condition)

```python
# Backend: NEVER discard conductor data
# FIX: SELECT FOR UPDATE prevents trip status change mid-batch
@transaction.atomic
def batch_sync(request):
    key = request.data['idempotency_key']
    if SyncLog.objects.filter(key=key).exists():
        return Response({'status': 'already_processed'}, 200)

    trip_id = request.data['trip_id']
    # LOCK the trip row — no other transaction can change status during this batch
    trip = Trip.objects.select_for_update().get(id=trip_id)

    tickets = request.data['tickets']

    if trip.status != 'in_progress':
        # Quarantine ALL tickets atomically (never fragment a batch)
        QuarantinedSync.objects.bulk_create([
            QuarantinedSync(
                original_data=ticket,
                reason=f'Trip status is {trip.status}',
                conductor=request.user,
                trip=trip
            ) for ticket in tickets
        ])
        SyncLog.objects.create(key=key, accepted=0, quarantined=len(tickets))
        return Response({
            'accepted': [],
            'quarantined': [t['ticket_number'] for t in tickets],
            'message': f'{len(tickets)} tickets quarantined (trip {trip.status})'
        })

    # INSERT ALL tickets atomically (ignore_conflicts for safety)
    created = PassengerTicket.objects.bulk_create(
        [PassengerTicket(**t) for t in tickets],
        ignore_conflicts=True
    )
    SyncLog.objects.create(key=key, accepted=len(created), quarantined=0)
    return Response({'accepted': [t.ticket_number for t in created], 'quarantined': []})
```

### Sync Edge Cases Handled

| Scenario | v2 (broken) | v3 (fixed) |
|----------|------------|------------|
| Battery dies mid-sync, retry | Duplicate data (new timestamp key) | Same content hash → "already_processed" |
| Admin cancels trip while conductor offline | 50 tickets rejected and LOST | 50 tickets quarantined for admin review |
| Two conductors on same trip | Second conductor's data lost (unique key conflict) | Both accepted (different ticket sequences per conductor) |
| Network timeout, app retries | Unknown state | Idempotent — safe to retry infinitely |

### Mobile Sync Settings

```kotlin
// User-controllable sync preferences
data class SyncPreferences(
    val syncOnlyOnWifi: Boolean = false,     // Save mobile data
    val maxBatchSize: Int = 50,              // Configurable
    val maxRetries: Int = 3,                 // Prevent infinite loops
    val retryBackoff: BackoffPolicy = EXPONENTIAL  // 1m, 2m, 4m
)

// Stale data warning
if (trip.lastSyncedAt.isBefore(now().minus(4.hours))) {
    showWarning("Trip data 4+ hours old. Server state may have changed.")
}
```

---

## Cargo State Machine (v3 — With Rollback)

```
                    ┌────── admin_override (any→any, with reason) ──────┐
                    │                                                     │
                    ▼                                                     │
created ──► in_transit ──► arrived ──► delivered ──► paid                │
    │           │              │           │           │                  │
    │           │              │           │           └──► refunded      │
    │           │              │           └──► refused                   │
    │           │              └──► lost                                  │
    │           └──► lost                                                │
    └──► cancelled (trip cancelled before departure)                     │
                                                                         │
                    ◄────── All states reachable via admin_override ──────┘
```

### Transition Rules

| Transition | Who | Conditions |
|-----------|-----|------------|
| created → in_transit | System | Auto on trip start |
| in_transit → arrived | System | Auto on trip complete |
| arrived → delivered | office_staff (cargo dept OK) | At destination office |
| delivered → paid | office_staff (cargo dept OK) | Payment collected |
| delivered → refused | office_staff (cargo dept OK) | Receiver refuses |
| any → lost | office_staff, admin | Cargo missing |
| created → cancelled | office_staff, admin | Before departure |
| **most → most** | **admin ONLY** | **With mandatory reason + audit, forbidden transitions blocked** |

### Admin Override Endpoint (with Financial Safeguards)

```python
# Transitions that create financial risk — blocked even for admin
FORBIDDEN_OVERRIDES = {
    ('paid', 'created'),       # Would enable double-collection from sender
    ('paid', 'in_transit'),    # Can't un-pay
    ('refunded', 'paid'),      # Can't un-refund
    ('refunded', 'delivered'), # Refund is final
    ('delivered', 'created'),  # Delivered goods can't un-deliver
    ('lost', 'delivered'),     # Pick one: it's lost OR delivered
}

@action(detail=True, methods=['post'], permission_classes=[IsAdmin])
def override_status(self, request, pk=None):
    """Admin override: most transitions allowed with reason, financial fraud blocked"""
    ticket = self.get_object()
    old_status = ticket.status
    new_status = request.data['new_status']
    reason = request.data['reason']  # REQUIRED

    if (old_status, new_status) in FORBIDDEN_OVERRIDES:
        raise ValidationError({
            'error': 'FORBIDDEN_TRANSITION',
            'message': f'{old_status}→{new_status} blocked (financial risk)',
            'suggestion': 'Create a correction/refund record instead'
        })

    if not reason or len(reason) < 10:
        raise ValidationError("Override reason must be at least 10 characters")

    ticket.status = new_status
    ticket.status_override_reason = reason
    ticket.status_override_by = request.user
    ticket.save()  # Audit middleware captures old_status → new_status + reason

    return Response({'old': old_status, 'new': new_status, 'audited': True})
```

---

## JWT Strategy (v3.1 — Platform-Specific + Instant Revoke)

### Problem: Refresh Token Rotation Creates Race Condition on Mobile

WorkManager background sync + UI thread both try to refresh → old token blacklisted → conductor logged out mid-trip.

### Solution: Different JWT strategy per platform

```python
# settings.py
SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME': timedelta(minutes=15),  # Short = stolen device window small
    'REFRESH_TOKEN_LIFETIME': timedelta(days=7),
    # Rotation handled per-platform in the view
}

# api/views/auth.py
class TokenRefreshView(TokenRefreshView):
    def post(self, request, *args, **kwargs):
        platform = request.data.get('platform', 'web')

        if platform == 'mobile':
            # NO rotation — prevents WorkManager/UI race condition
            # Security: 15min access + device_id binding compensates
            return refresh_without_rotation(request)
        else:
            # Web: rotate + blacklist (single tab, no race)
            return refresh_with_rotation(request)
```

### Mobile Token Refresh (Thread-Safe)

```kotlin
// Single mutex prevents race between UI thread and WorkManager
private val refreshMutex = Mutex()

suspend fun getValidAccessToken(): String {
    refreshMutex.withLock {
        if (currentToken.isValid()) return currentToken.value
        val newToken = api.refreshToken(refreshToken, platform = "mobile")
        tokenStore.save(newToken)
        return newToken.accessToken
    }
}
```

### Device Binding + Instant Revoke (Stolen Phone Protection)

```python
# Login binds JWT to device
class LoginSerializer(serializers.Serializer):
    phone = serializers.CharField()
    password = serializers.CharField()
    device_id = serializers.CharField(required=False)  # Android ID

# Token payload includes device_id
payload = {
    'user_id': user.id,
    'role': user.role,
    'device_id': device_id,
}

# Admin can revoke a device
POST /api/admin/users/{id}/revoke-device/
# → Adds device_id to revoked_devices table

# MIDDLEWARE: Checks EVERY request (kills active sessions instantly)
class DeviceRevocationMiddleware:
    """Checks if device_id in JWT has been revoked. Redis-cached for speed."""
    def __call__(self, request):
        if request.user.is_authenticated and hasattr(request, 'auth'):
            device_id = request.auth.get('device_id')
            if device_id:
                cache_key = f'revoked_device:{device_id}'
                if cache.get(cache_key):
                    raise AuthenticationFailed('Device revoked')
        return self.get_response(request)

# On revoke: set cache key (fast lookup, no DB query per request)
def revoke_device(device_id):
    RevokedDevice.objects.create(device_id=device_id)
    cache.set(f'revoked_device:{device_id}', True, timeout=86400 * 30)  # 30 days
```

---

## Currency-Aware Storage

### Problem: INTEGER without metadata is a time bomb for future expansion

### Solution: Currency columns + SQL comments (zero-cost, future-proof)

```sql
-- All tables with money columns get these two columns:
ALTER TABLE trips ADD COLUMN currency CHAR(3) DEFAULT 'DZD' NOT NULL;
ALTER TABLE pricing_config ADD COLUMN currency CHAR(3) DEFAULT 'DZD' NOT NULL;
ALTER TABLE passenger_tickets ADD COLUMN currency CHAR(3) DEFAULT 'DZD' NOT NULL;
ALTER TABLE cargo_tickets ADD COLUMN currency CHAR(3) DEFAULT 'DZD' NOT NULL;
ALTER TABLE trip_expenses ADD COLUMN currency CHAR(3) DEFAULT 'DZD' NOT NULL;

-- SQL comments for auditor clarity
COMMENT ON COLUMN passenger_tickets.price IS
    'Integer amount in smallest currency unit. DZD has 0 decimal places: 2500 = 2,500 DZD';
COMMENT ON COLUMN trips.passenger_base_price IS
    'Snapshot from pricing_config at trip creation. Immutable after trip starts.';
```

### Application Layer

```python
# models/mixins.py
class MoneyFieldMixin:
    """All money fields: INTEGER + currency CHAR(3) + display helper"""
    def display_price(self, amount, currency='DZD'):
        if currency == 'DZD':
            return f"{amount:,} DA"  # 2,500 DA
        return f"{amount} {currency}"
```

---

## Celery Task Safety (v3.1)

```python
@shared_task(
    bind=True,
    time_limit=600,          # 10 min hard kill
    soft_time_limit=540,     # 9 min graceful shutdown
    max_retries=0,           # No auto-retry (user re-requests)
    rate_limit='2/m',        # Max 2 exports per minute globally
)
def generate_excel_export(self, user_id, filters, max_rows=100_000):
    qs = build_queryset(filters)
    count = qs.count()

    if count > max_rows:
        raise ValueError(f"Export exceeds {max_rows:,} rows. Use date filters to narrow.")

    # write_only=True: rows flushed to file buffer, not kept in memory
    workbook = openpyxl.Workbook(write_only=True)
    sheet = workbook.create_sheet()

    for i, row in enumerate(qs.iterator(chunk_size=1000)):
        sheet.append(serialize_row(row))

    path = f'/exports/{user_id}/{int(time.time())}.xlsx'
    workbook.save(path)

    # Cleanup old exports (prevent disk fill)
    cleanup_old_exports(user_id, keep_last=5)

    ExportResult.objects.create(user_id=user_id, path=path, rows=count)
    return path
```

---

## Database Schema (11 tables)

### New/Updated Tables

```sql
-- NEW: quarantined_syncs (rejected sync data for admin review)
CREATE TABLE quarantined_syncs (
    id SERIAL PRIMARY KEY,
    conductor_id INTEGER NOT NULL REFERENCES users(id),
    trip_id INTEGER NOT NULL REFERENCES trips(id),
    original_data JSONB NOT NULL,        -- Full ticket data as received
    reason TEXT NOT NULL,                 -- Why quarantined
    status VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    reviewed_by INTEGER REFERENCES users(id),
    reviewed_at TIMESTAMP,
    review_notes TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- UPDATED: cargo_tickets (state machine + override fields)
CREATE TABLE cargo_tickets (
    id SERIAL PRIMARY KEY,
    trip_id INTEGER NOT NULL REFERENCES trips(id),
    ticket_number VARCHAR(20) NOT NULL,
    sender_name VARCHAR(100) NOT NULL,
    sender_phone VARCHAR(20),
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(20),
    cargo_tier VARCHAR(10) NOT NULL CHECK (cargo_tier IN ('small', 'medium', 'large')),
    description TEXT,
    price INTEGER NOT NULL,
    currency CHAR(3) DEFAULT 'DZD' NOT NULL,
    payment_source VARCHAR(20) NOT NULL CHECK (payment_source IN ('prepaid', 'pay_on_delivery')),
    status VARCHAR(20) DEFAULT 'created' CHECK (status IN (
        'created', 'in_transit', 'arrived', 'delivered', 'paid', 'refused', 'lost', 'cancelled', 'refunded'
    )),
    status_override_reason TEXT,
    status_override_by INTEGER REFERENCES users(id),
    delivered_at TIMESTAMP,
    delivered_by INTEGER REFERENCES users(id),
    created_by INTEGER NOT NULL REFERENCES users(id),
    version INTEGER DEFAULT 1,
    synced_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(trip_id, ticket_number)
);

-- UPDATED: users (device_id for stolen phone protection)
ALTER TABLE users ADD COLUMN device_id VARCHAR(64);
ALTER TABLE users ADD COLUMN device_bound_at TIMESTAMP;
```

### Full Table List (11)

| # | Table | Purpose |
|---|-------|---------|
| 1 | `offices` | 5 offices with city and contact info |
| 2 | `users` | Accounts with role + department + device_id |
| 3 | `buses` | Fleet inventory per office |
| 4 | `trips` | Trip lifecycle with price snapshot + currency |
| 5 | `passenger_tickets` | Passenger records with version + currency |
| 6 | `cargo_tickets` | Cargo records with 9-state machine + override fields |
| 7 | `trip_expenses` | Conductor expenses with currency |
| 8 | `audit_log` | Immutable (REVOKE DELETE/UPDATE), JSONB old/new |
| 9 | `pricing_config` | Route pricing with currency |
| 10 | `sync_log` | Idempotency key tracking (content hash) |
| 11 | `quarantined_syncs` | Rejected sync data awaiting admin review |

### Indexes

```sql
CREATE INDEX idx_trips_office_date ON trips(origin_office_id, departure_datetime DESC);
CREATE INDEX idx_trips_conductor_status ON trips(conductor_id, status)
    WHERE status IN ('scheduled', 'in_progress');
CREATE INDEX idx_cargo_unpaid ON cargo_tickets(trip_id, status)
    WHERE status NOT IN ('paid', 'refused', 'cancelled');
CREATE INDEX idx_audit_timestamp ON audit_log(created_at DESC, user_id);
CREATE INDEX idx_passenger_trip ON passenger_tickets(trip_id);
CREATE INDEX idx_cargo_trip ON cargo_tickets(trip_id);
CREATE INDEX idx_expense_trip ON trip_expenses(trip_id);
CREATE INDEX idx_quarantine_status ON quarantined_syncs(status, created_at DESC);
```

---

## API Endpoints (Complete)

### Auth
| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/auth/login/` | Rate limited 10/min, returns device_id binding |
| POST | `/api/auth/token/refresh/` | `platform=web` → rotate; `platform=mobile` → no rotate |
| GET | `/api/auth/me/` | Returns role + department + office + device |
| POST | `/api/admin/users/{id}/revoke-device/` | Admin: invalidate stolen device |

### Trips
| Method | Path | Access |
|--------|------|--------|
| GET | `/api/trips/` | office_staff, admin (scoped) |
| POST | `/api/trips/` | office_staff (dept≠cargo), admin |
| GET | `/api/trips/{id}/` | scoped by role |
| PATCH | `/api/trips/{id}/start/` | conductor (own, scheduled) |
| PATCH | `/api/trips/{id}/complete/` | conductor (own, in_progress) |
| PATCH | `/api/trips/{id}/cancel/` | office_staff, admin |

### Tickets
| Method | Path | Access |
|--------|------|--------|
| GET | `/api/trips/{id}/passenger-tickets/` | dept≠cargo staff, admin, conductor (own) |
| POST | `/api/trips/{id}/passenger-tickets/` | conductor (in_progress), staff, admin |
| PATCH | `/api/passenger-tickets/{id}/` | admin/staff (completed trips only) |
| GET | `/api/trips/{id}/cargo-tickets/` | all staff (scoped) |
| POST | `/api/trips/{id}/cargo-tickets/` | conductor, staff, admin |
| PATCH | `/api/cargo-tickets/{id}/` | admin/staff (completed trips only) |
| PATCH | `/api/cargo-tickets/{id}/receive/` | office_staff |
| PATCH | `/api/cargo-tickets/{id}/status/` | office_staff (forward transitions) |
| POST | `/api/cargo-tickets/{id}/override-status/` | admin ONLY (any→any + reason) |

### Expenses
| Method | Path | Access |
|--------|------|--------|
| GET | `/api/trips/{id}/expenses/` | office_staff, admin, conductor |
| POST | `/api/trips/{id}/expenses/` | conductor (in_progress) |

### Sync
| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/sync/batch/` | Content-hash idempotency, atomic, quarantine |

### Reports
| Method | Path | Access |
|--------|------|--------|
| GET | `/api/reports/daily/` | office_staff, admin (prefetch, ≤5 queries) |
| GET | `/api/reports/trip/{id}/` | office_staff, admin |
| GET | `/api/reports/route-analysis/` | admin |
| POST | `/api/reports/export/` | office_staff, admin → Celery task_id |
| GET | `/api/reports/export/{task_id}/` | Poll status + download |

### Admin
| Method | Path | Access |
|--------|------|--------|
| CRUD | `/api/admin/users/` | admin |
| CRUD | `/api/admin/buses/` | admin |
| CRUD | `/api/admin/offices/` | admin |
| CRUD | `/api/admin/pricing/` | admin (invalidates cache) |
| GET | `/api/admin/audit-log/` | admin |
| GET | `/api/admin/quarantine/` | admin, office_staff |
| POST | `/api/admin/quarantine/{id}/approve/` | admin (idempotent: `status=pending` check) |
| POST | `/api/admin/quarantine/{id}/reject/` | admin (idempotent: `status=pending` check) |

---

## System Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                          CLIENTS                                │
│                                                                 │
│  ┌──────────────────┐          ┌──────────────────────────┐    │
│  │  Android App     │          │   Web Portal (SPA)        │    │
│  │  Kotlin + Room   │          │   React + Vite + TS       │    │
│  │                  │          │                            │    │
│  │  ┌────────────┐  │          │ Office │ Admin │ Cargo     │    │
│  │  │ SQLite     │  │          │        │       │           │    │
│  │  │ (offline)  │  │          └───────┬──────────────────┘    │
│  │  └──────┬─────┘  │                 │                        │
│  │  WorkManager +   │                 │                        │
│  │  RefreshMutex    │                 │                        │
│  │  SyncPreferences │                 │                        │
│  └─────────┬────────┘                 │                        │
│            │        HTTPS + JWT       │                        │
├────────────┼──────────────────────────┼────────────────────────┤
│            ▼                          ▼                        │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │              Django REST Framework                        │ │
│  │                                                           │ │
│  │  ┌──────────┐ ┌───────────────┐ ┌──────────┐            │ │
│  │  │JWT Auth  │ │ DRF Permission│ │ Audit    │            │ │
│  │  │2h access │ │ Classes (RBAC)│ │ Middleware│            │ │
│  │  │web:rotate│ │ + TripStatus  │ │ (append) │            │ │
│  │  │mob:fixed │ │ + Department  │ └──────────┘            │ │
│  │  └──────────┘ └───────────────┘                          │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────────────────┐ │ │
│  │  │Throttle  │ │ Celery   │ │ Django ORM               │ │ │
│  │  │10/min    │ │ 10min TL │ │ select_related           │ │ │
│  │  │1000/hr   │ │ 100K max │ │ prefetch_related         │ │ │
│  │  └──────────┘ │ 2/m rate │ │ .iterator(chunk=1000)    │ │ │
│  │               └──────────┘ └────────────┬─────────────┘ │ │
│  └─────────────────────────────────────────┼───────────────┘ │
│                                            │                   │
│  ┌────────────────────┐  ┌─────────────────▼────────────────┐ │
│  │  Redis             │  │  PostgreSQL 16                    │ │
│  │  • Cache (pricing) │  │  • 11 tables (+ quarantined)     │ │
│  │  • Celery broker   │  │  • INTEGER prices + currency     │ │
│  │  • Token blacklist │  │  • Content-hash idempotency      │ │
│  └────────────────────┘  │  • Append-only audit_log         │ │
│                           │  • Composite indexes             │ │
│                           └──────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

---

## File Structure

```
SOUIGAT/
├── docs/                           # Specs & prototypes
│   ├── SOUIGAT-Complete-System-Report.md
│   └── Basic UI&UX/
│
├── backend/
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── manage.py
│   ├── souigat/
│   │   ├── settings.py             # JWT, throttling, celery, cache
│   │   ├── settings_production.py  # Admin disabled/restricted
│   │   ├── urls.py
│   │   ├── celery.py
│   │   └── wsgi.py
│   └── api/
│       ├── models/                  # 11 models
│       │   ├── office.py
│       │   ├── user.py              # role + department + device_id
│       │   ├── bus.py
│       │   ├── trip.py              # price snapshot + currency
│       │   ├── passenger_ticket.py  # version, composite unique, currency
│       │   ├── cargo_ticket.py      # 9-state machine, override fields
│       │   ├── trip_expense.py      # currency
│       │   ├── audit_log.py         # append-only
│       │   ├── pricing_config.py    # currency
│       │   ├── sync_log.py          # content-hash idempotency
│       │   └── quarantined_sync.py  # rejected sync data
│       ├── serializers/
│       ├── views/
│       │   ├── auth.py              # Platform-specific JWT refresh
│       │   ├── trips.py
│       │   ├── passenger_tickets.py
│       │   ├── cargo_tickets.py     # + override_status endpoint
│       │   ├── expenses.py
│       │   ├── sync.py              # Quarantine logic
│       │   ├── reports.py           # prefetch_related
│       │   ├── admin_views.py       # Quarantine review, device revoke
│       │   └── export.py            # Celery task dispatch
│       ├── permissions.py           # RBAC + TripStatus + Department
│       ├── middleware/
│       │   └── audit.py
│       ├── tasks.py                 # Celery (Excel with safety limits)
│       └── tests/
│
├── web/                            # React + Vite + TypeScript
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── pages/
│       │   ├── Login.tsx
│       │   ├── office/
│       │   ├── admin/
│       │   │   ├── QuarantineReview.tsx  # Review rejected syncs
│       │   │   └── ...
│       │   └── cargo/
│       ├── components/
│       ├── services/
│       ├── hooks/
│       └── types/
│
├── mobile/                         # Kotlin Android (Phase 3)
│
├── docker-compose.yml              # PG + Redis + Django + Celery worker
├── backup.sh                       # pg_dump daily cron
├── .env.example
├── .gitignore
└── README.md
```

---

## Tasks

### Phase 1: Backend Foundation (T1-T20)

- [ ] **T1**: Docker scaffold → `docker-compose.yml` (PG + Redis + Django + Celery worker)
  - Verify: `docker compose up` → all 4 services running
- [ ] **T2**: Django project + DRF + simplejwt + celery config
  - Verify: `python manage.py runserver` → 200 at `/api/`
- [ ] **T3**: Models (11 tables) → role enum (4), department, device_id, INTEGER prices, currency CHAR(3), version, composite unique, cargo 9-state enum, quarantined_syncs
  - Verify: `migrate` → 11 tables + all indexes + SQL comments on price cols
- [ ] **T4**: Seed data → 5 offices, admin user, pricing_config (DZD), test bus
  - Verify: `loaddata seed` → data visible in DB
- [ ] **T5**: Django Admin → all models (production: disabled or IP-restricted)
  - Verify: `/admin/` → all tables; production setting disables it
- [ ] **T6**: JWT Auth → 15min access, 7d refresh, platform-specific rotation, device binding, revoke middleware
  - Verify: `platform=web` rotates; `platform=mobile` doesn't; expired → 401; revoked device → instant 401
- [ ] **T7**: RBAC permission classes → role + department scoping + trip status check
  - Verify: cargo dept → 403 on passenger; edit in-progress ticket → 403
- [ ] **T8**: Throttling → 10/min anon, 1000/hr user
  - Verify: 11th login in 1 min → 429
- [ ] **T9**: Trip CRUD → price snapshot (copies pricing + currency), start, complete, cancel
  - Verify: trip.currency = 'DZD'; trip.passenger_base_price matches config
- [ ] **T10**: Passenger tickets → composite unique, trip status validation, version field
  - Verify: create on completed trip → 403; duplicate → conflict
- [ ] **T11**: Cargo tickets → 9-state machine, forward transitions, admin override with reason
  - Verify: `override_status` requires reason ≥10 chars; all transitions audited
- [ ] **T12**: Expenses → conductor only, in-progress trips only, currency field
  - Verify: expense on completed trip → 403
- [ ] **T13**: Batch sync → immutable content-hash, `@transaction.atomic`, `select_for_update()`, quarantine
  - Verify: same content → "already_processed"; cancelled trip → ALL quarantined (never fragmented); concurrent trip status change → blocked by row lock
- [ ] **T14**: Quarantine review → admin approve/reject (idempotent: pending-check + get_or_create)
  - Verify: approve → ticket created; approve again → no duplicate; reject → marked rejected
- [ ] **T15**: Reports → daily (prefetch_related, ≤5 queries), trip detail
  - Verify: 50 trips → ≤5 SQL queries (Django debug toolbar)
- [ ] **T16**: Excel export → Celery with time_limit=600, max_rows=100K, iterator streaming
  - Verify: 10K rows completes; 200K rows → "use date filters" error
- [ ] **T17**: Audit middleware → append-only, JSONB old/new
  - Verify: edit ticket → audit row; SQL DELETE on audit_log → permission denied
- [ ] **T18**: Pricing cache → Redis, invalidate on admin update
  - Verify: update pricing → cache invalidated; next trip uses new price
- [ ] **T19**: DB security → REVOKE on audit_log, SQL comments on all price columns
  - Verify: `\d+ passenger_tickets` shows column comments
- [ ] **T20**: Device revoke → admin endpoint to invalidate stolen device tokens
  - Verify: revoke device → that device's tokens rejected

### Phase 2: Web Portal (T21-T29)

- [ ] **T21**: Scaffold + auth → Vite + React + TS, login, JWT interceptor, role routing
- [ ] **T22**: Office dashboard → metrics, trip table
- [ ] **T23**: Trip management → create, detail, cancel
- [ ] **T24**: Financial reports → daily, async Excel download with polling
- [ ] **T25**: Cargo department view → cargo-only pages, receive, status transitions
- [ ] **T26**: Admin dashboard → cross-office, user CRUD, bus CRUD, pricing CRUD
- [ ] **T27**: Admin audit log → filterable viewer
- [ ] **T28**: Quarantine review page → approve/reject rejected syncs
- [ ] **T29**: Trip status UX → disable edit on in-progress, stale data warnings

### Phase 3: Android Mobile (T30-T35)

- [ ] **T30**: Kotlin project → Room, Retrofit, WorkManager, Material 3
- [ ] **T31**: Auth → JWT in EncryptedSharedPreferences, refresh mutex, device binding
- [ ] **T32**: Trip dashboard → start/end, stale data warning (4h+)
- [ ] **T33**: Ticket creation → `PT-{TRIP_ID}-{SEQ}`, cargo state machine
- [ ] **T34**: Expense recording
- [ ] **T35**: Background sync → content-hash idempotency, user-controlled preferences (WiFi-only, batch size, max retries), exponential backoff

### Phase X: Verification

- [ ] RBAC: each role + department combination tested
- [ ] Trip price freeze: edit in-progress → 403
- [ ] Sync collision: 2 conductors same day → no collision
- [ ] Sync quarantine: cancelled trip sync → data quarantined, not lost
- [ ] Idempotency: battery-death retry → no duplicates
- [ ] Cargo state: all valid transitions + admin override + audit
- [ ] Audit immutability: SQL DELETE → denied
- [ ] Financial accuracy: manual DZD calculation = report output
- [ ] Excel export: 10K rows < 60s, 200K → error message
- [ ] JWT: mobile refresh no rotation, web rotates
- [ ] Device revoke: stolen device → tokens rejected
- [ ] DB backup: `backup.sh` produces valid dump

---

## Done When

- [ ] All 31 review items addressed (3 rounds)
- [ ] Zero data loss: quarantine atomic (never fragmented), idempotency on immutable fields
- [ ] Conductor works offline with user-controlled sync settings
- [ ] Department-scoped users see only their data subset
- [ ] Cargo state machine: admin override with forbidden financial transitions
- [ ] Financial reports accurate with currency metadata
- [ ] Audit trail immutable at database level
- [ ] Excel export safe under load (Celery limits + disk cleanup)
- [ ] Stolen device revoked instantly (middleware + Redis cache)

# Phase 1: Backend Foundation (v2 — Audit-Hardened)

## Goal
Complete Django REST backend: 11 models (with timestamps, soft delete, on_delete strategy), JWT auth (device-bound), RBAC, trip lifecycle, tickets (passenger + 9-state cargo), sync with quarantine, reports, Excel export, audit trail, and device revocation.

## Pre-Requisites
- Phase 0 complete ✅ (5 Docker services running)
- Architecture v3.1 as source of truth

---

## Tasks (T1–T20)

### Week 1: Models & Data Layer (T1–T5)

---

- [ ] **T1**: Set `AUTH_USER_MODEL` before any migration

  > ⚠️ **MUST be done before first `migrate`**. If already migrated, drop DB and start fresh.

  ```python
  # settings.py — add:
  AUTH_USER_MODEL = 'api.User'
  ```
  → Verify: Setting present in settings.py

---

- [ ] **T2**: Create model package with all 11 models
  ```
  api/models/
  ├── __init__.py          # Import all models
  ├── mixins.py            # TimestampMixin, SoftDeleteMixin
  ├── office.py
  ├── user.py              # CustomUser (AbstractUser, username=None)
  ├── bus.py
  ├── trip.py              # Price snapshot + currency
  ├── passenger_ticket.py  # Version + composite unique
  ├── cargo_ticket.py      # 9-state machine + transition table
  ├── trip_expense.py      # Currency
  ├── audit_log.py         # Append-only (no soft delete)
  ├── pricing_config.py    # Currency + effective dates
  ├── sync_log.py          # Content-hash idempotency
  └── quarantined_sync.py  # Rejected sync data
  ```

  #### Base Mixins — `mixins.py`
  ```python
  class TimestampMixin(models.Model):
      created_at = models.DateTimeField(auto_now_add=True, db_index=True)
      updated_at = models.DateTimeField(auto_now=True)
      class Meta:
          abstract = True

  class SoftDeleteManager(models.Manager):
      def get_queryset(self):
          return super().get_queryset().filter(is_deleted=False)

  class SoftDeleteMixin(models.Model):
      is_deleted = models.BooleanField(default=False)
      deleted_at = models.DateTimeField(null=True, blank=True)
      objects = SoftDeleteManager()
      all_objects = models.Manager()
      class Meta:
          abstract = True
      def soft_delete(self):
          self.is_deleted = True
          self.deleted_at = timezone.now()
          self.save(update_fields=['is_deleted', 'deleted_at'])
  ```
  > All financial models inherit **both** `TimestampMixin` + `SoftDeleteMixin`.
  > `AuditLog` inherits only `TimestampMixin` (never deleted).

  #### Model Schemas (with on_delete + timestamps)

  **Office** — inherits `TimestampMixin, SoftDeleteMixin`
  | Field | Type | Constraints |
  |-------|------|-------------|
  | name | VARCHAR(100) | unique |
  | city | VARCHAR(100) | |
  | address | TEXT | nullable |
  | phone | VARCHAR(20) | nullable |
  | is_active | BOOLEAN | default=True |

  **User** — `AbstractUser` with `username=None`
  ```python
  class User(AbstractUser):
      username = None  # Remove Django's default username
      phone = models.CharField(max_length=20, unique=True)
      USERNAME_FIELD = 'phone'
      REQUIRED_FIELDS = ['first_name', 'last_name']
  ```
  | Field | Type | Constraints |
  |-------|------|-------------|
  | phone | VARCHAR(20) | unique, **login identifier** |
  | role | VARCHAR(20) | choices: `admin`, `office_staff`, `conductor`, `driver` |
  | department | VARCHAR(20) | nullable, choices: `all`, `cargo`, `passenger` |
  | office | FK(Office) | nullable, **on_delete=SET_NULL** |
  | device_id | VARCHAR(64) | nullable |
  | device_bound_at | DATETIME | nullable |

  **Bus** — inherits `TimestampMixin, SoftDeleteMixin`
  | Field | Type | Constraints |
  |-------|------|-------------|
  | plate_number | VARCHAR(20) | unique |
  | office | FK(Office) | **on_delete=PROTECT** |
  | capacity | INTEGER | |
  | is_active | BOOLEAN | default=True |

  **Trip** — inherits `TimestampMixin, SoftDeleteMixin`
  | Field | Type | Constraints |
  |-------|------|-------------|
  | origin_office | FK(Office) | **on_delete=PROTECT** |
  | destination_office | FK(Office) | **on_delete=PROTECT** |
  | bus | FK(Bus) | **on_delete=PROTECT** |
  | conductor | FK(User) | **on_delete=PROTECT** |
  | departure_datetime | DATETIME | |
  | arrival_datetime | DATETIME | nullable |
  | status | VARCHAR(20) | `scheduled`, `in_progress`, `completed`, `cancelled` |
  | passenger_base_price | INTEGER | frozen from pricing_config |
  | cargo_small_price | INTEGER | frozen |
  | cargo_medium_price | INTEGER | frozen |
  | cargo_large_price | INTEGER | frozen |
  | currency | CHAR(3) | default='DZD' |

  Validations (in `clean()`):
  ```python
  def clean(self):
      if self.origin_office == self.destination_office:
          raise ValidationError("Origin and destination must differ")
      if self.conductor and self.conductor.role != 'conductor':
          raise ValidationError("Assigned user must be a conductor")
      if self.arrival_datetime and self.arrival_datetime <= self.departure_datetime:
          raise ValidationError("Arrival must be after departure")
  ```

  Manager:
  ```python
  class TripManager(SoftDeleteManager):
      def active(self):
          return self.filter(status__in=['scheduled', 'in_progress'])
      def for_office(self, office):
          return self.filter(Q(origin_office=office) | Q(destination_office=office))
      def for_conductor(self, user):
          return self.filter(conductor=user)
  ```

  **PassengerTicket** — inherits `TimestampMixin, SoftDeleteMixin`
  | Field | Type | Constraints |
  |-------|------|-------------|
  | trip | FK(Trip) | **on_delete=PROTECT** |
  | ticket_number | VARCHAR(20) | composite unique with trip |
  | passenger_name | VARCHAR(100) | |
  | price | INTEGER | CHECK > 0 |
  | currency | CHAR(3) | default='DZD' |
  | payment_source | VARCHAR(20) | `cash`, `prepaid` |
  | seat_number | VARCHAR(10) | nullable |
  | status | VARCHAR(20) | `active`, `cancelled`, `refunded` |
  | created_by | FK(User) | **on_delete=PROTECT** |
  | version | INTEGER | default=1 |
  | synced_at | DATETIME | nullable |

  **CargoTicket** — inherits `TimestampMixin, SoftDeleteMixin`
  | Field | Type | Constraints |
  |-------|------|-------------|
  | trip | FK(Trip) | **on_delete=PROTECT** |
  | ticket_number | VARCHAR(20) | composite unique with trip |
  | sender_name | VARCHAR(100) | |
  | sender_phone | VARCHAR(20) | nullable |
  | receiver_name | VARCHAR(100) | |
  | receiver_phone | VARCHAR(20) | nullable |
  | cargo_tier | VARCHAR(10) | `small`, `medium`, `large` |
  | description | TEXT | nullable |
  | price | INTEGER | CHECK > 0 |
  | currency | CHAR(3) | default='DZD' |
  | payment_source | VARCHAR(20) | `prepaid`, `pay_on_delivery` |
  | status | VARCHAR(20) | default='created', 9 states |
  | status_override_reason | TEXT | nullable |
  | status_override_by | FK(User) | nullable, **on_delete=SET_NULL** |
  | delivered_at | DATETIME | nullable |
  | delivered_by | FK(User) | nullable, **on_delete=SET_NULL** |
  | created_by | FK(User) | **on_delete=PROTECT** |
  | version | INTEGER | default=1 |
  | synced_at | DATETIME | nullable |

  State transition table (enforced in model method):
  ```python
  VALID_TRANSITIONS = {
      'created':    ['in_transit', 'cancelled'],
      'in_transit': ['arrived', 'lost'],
      'arrived':    ['delivered', 'refused'],
      'delivered':  ['paid', 'refunded'],
      'paid':       [],           # terminal
      'refused':    ['cancelled'],
      'lost':       [],           # terminal
      'cancelled':  [],           # terminal
      'refunded':   [],           # terminal
  }

  FORBIDDEN_ADMIN_OVERRIDES = {
      ('paid', 'created'), ('paid', 'in_transit'),
      ('refunded', 'created'), ('refunded', 'delivered'),
      ('delivered', 'created'), ('lost', 'delivered'),
  }

  def transition_to(self, new_status, user, reason=None):
      if new_status not in self.VALID_TRANSITIONS.get(self.status, []):
          if user.role != 'admin':
              raise ValidationError(f"Cannot transition {self.status} → {new_status}")
          if (self.status, new_status) in self.FORBIDDEN_ADMIN_OVERRIDES:
              raise ValidationError("Forbidden financial transition")
          if not reason or len(reason) < 10:
              raise ValidationError("Override reason must be ≥10 characters")
      self.status = new_status
      self.save()
  ```

  **TripExpense** — inherits `TimestampMixin`
  | Field | Type | Constraints |
  |-------|------|-------------|
  | trip | FK(Trip) | **on_delete=CASCADE** |
  | description | VARCHAR(200) | |
  | amount | INTEGER | CHECK > 0 |
  | currency | CHAR(3) | default='DZD' |
  | created_by | FK(User) | **on_delete=PROTECT** |

  **AuditLog** — inherits `TimestampMixin` only (NO soft delete, NO hard delete)
  | Field | Type | Constraints |
  |-------|------|-------------|
  | user | FK(User) | nullable, **on_delete=SET_NULL** |
  | action | VARCHAR(20) | `create`, `update`, `delete`, `override` |
  | table_name | VARCHAR(50) | |
  | record_id | INTEGER | |
  | old_values | JSONB | nullable |
  | new_values | JSONB | nullable |
  | ip_address | GenericIPAddress | nullable |
  | created_at | DATETIME | auto_now_add, **indexed** |

  **PricingConfig** — inherits `TimestampMixin, SoftDeleteMixin`
  | Field | Type | Constraints |
  |-------|------|-------------|
  | origin_office | FK(Office) | **on_delete=PROTECT** |
  | destination_office | FK(Office) | **on_delete=PROTECT** |
  | passenger_price | INTEGER | |
  | cargo_small_price | INTEGER | |
  | cargo_medium_price | INTEGER | |
  | cargo_large_price | INTEGER | |
  | currency | CHAR(3) | default='DZD' |
  | effective_from | DATE | required |
  | effective_until | DATE | nullable (null = forever) |
  | is_active | BOOLEAN | default=True |
  | unique_together | | `(origin_office, destination_office, effective_from)` |

  Manager:
  ```python
  def get_active_pricing(self, origin, destination, date=None):
      date = date or timezone.now().date()
      return self.filter(
          origin_office=origin, destination_office=destination,
          effective_from__lte=date, is_active=True
      ).filter(
          Q(effective_until__isnull=True) | Q(effective_until__gte=date)
      ).order_by('-effective_from').first()
  ```

  **SyncLog** — inherits `TimestampMixin`
  | Field | Type | Constraints |
  |-------|------|-------------|
  | key | VARCHAR(64) | unique (content hash) |
  | conductor | FK(User) | **on_delete=PROTECT** |
  | trip | FK(Trip) | **on_delete=PROTECT** |
  | accepted | INTEGER | |
  | quarantined | INTEGER | |

  **QuarantinedSync** — inherits `TimestampMixin`
  | Field | Type | Constraints |
  |-------|------|-------------|
  | conductor | FK(User) | **on_delete=PROTECT** |
  | trip | FK(Trip) | **on_delete=PROTECT** |
  | original_data | JSONB | |
  | reason | TEXT | |
  | status | VARCHAR(20) | `pending`, `approved`, `rejected` |
  | reviewed_by | FK(User) | nullable, **on_delete=SET_NULL** |
  | reviewed_at | DATETIME | nullable |
  | review_notes | TEXT | nullable |

  → Verify: `makemigrations` + `migrate` → 11 custom tables + timestamp columns on all
  → Verify: `User.USERNAME_FIELD == 'phone'`

---

- [ ] **T3**: Add database indexes + constraints (migration)

  **Indexes:**
  ```sql
  idx_trips_office_date       ON trips(origin_office_id, departure_datetime DESC)
  idx_trips_conductor_status  ON trips(conductor_id, status) WHERE status IN ('scheduled','in_progress')
  idx_cargo_unpaid            ON cargo_tickets(trip_id, status) WHERE status NOT IN ('paid','refused','cancelled')
  idx_audit_timestamp         ON audit_log(created_at DESC, user_id)
  idx_passenger_trip          ON passenger_tickets(trip_id)
  idx_cargo_trip              ON cargo_tickets(trip_id)
  idx_expense_trip            ON trip_expenses(trip_id)
  idx_quarantine_status       ON quarantined_syncs(status, created_at DESC)
  ```

  **DB-level constraints (RunSQL in migration):**
  ```sql
  ALTER TABLE passenger_tickets ADD CONSTRAINT pt_price_positive CHECK (price > 0);
  ALTER TABLE cargo_tickets ADD CONSTRAINT ct_price_positive CHECK (price > 0);
  ALTER TABLE trip_expenses ADD CONSTRAINT te_amount_positive CHECK (amount > 0);
  ALTER TABLE trips ADD CONSTRAINT trip_arrival_after_departure
      CHECK (arrival_datetime IS NULL OR arrival_datetime > departure_datetime);
  ALTER TABLE trips ADD CONSTRAINT trip_different_offices
      CHECK (origin_office_id != destination_office_id);
  ```
  → Verify: `\di` shows 8 indexes; insert negative price → constraint violation

---

- [ ] **T4**: Register all models in Django Admin
  ```python
  # admin.py
  # All models: list_display includes created_at, list_filter, search_fields
  # AuditLogAdmin: readonly_fields (no add/change/delete permissions)
  # CargoTicketAdmin: readonly override fields, status color display
  # UserAdmin: extends UserAdmin for custom fields
  ```
  → Verify: `/admin/` shows all 11 models with filters

---

- [ ] **T5**: Seed data management command
  ```python
  # management/commands/seed_data.py
  # Creates:
  #   5 offices (Algiers, Oran, Constantine, Annaba, Sétif)
  #   1 admin user (phone: 0500000001 / password: admin123 — dev only)
  #   5 pricing configs (per route pair, effective_from=today)
  #   2 buses (one per office)
  #   2 office_staff (dept=all), 1 office_staff (dept=cargo)
  #   2 conductors
  ```
  → Verify: `docker compose exec backend python manage.py seed_data`
  → Verify: Admin panel shows seeded data, login with phone works

---

### Week 2: Auth & Permissions (T6–T8)

---

- [ ] **T6**: JWT Auth system
  - Login: phone + password → binds `device_id` to payload → returns access+refresh
  - Platform-specific refresh: `platform=web` → rotate; `platform=mobile` → no rotate
  - `/api/auth/me/` → role, department, office, device info

  Files:
  ```
  api/serializers/auth.py     # LoginSerializer (phone, password, device_id, platform)
  api/views/auth.py           # LoginView, PlatformTokenRefreshView, MeView
  ```

  Validations:
  - Login: rate limited 10/min (anon throttle)
  - device_id required for mobile platform
  - Phone format validation

  → Verify: Login returns access+refresh; web refresh rotates; mobile doesn't; expired → 401

---

- [ ] **T7**: RBAC permission classes
  ```python
  # api/permissions.py — 4 classes:
  RBACPermission          # Role-based action access (uses view.required_permission)
  TripStatusPermission    # No edits on in_progress/scheduled trips
  DepartmentPermission    # cargo dept → no passenger ticket access
  OfficeScopePermission   # Filters queryset to user's office
  ```
  → Verify: cargo dept → 403 on passenger; edit in-progress ticket → 403; office_staff sees only own office

---

- [ ] **T8**: Device Revocation Middleware
  ```python
  # api/middleware/device_revocation.py
  # Checks JWT device_id against Redis cache
  # cache key: f'revoked_device:{device_id}'
  # Revoked → AuthenticationFailed
  ```
  - Add to MIDDLEWARE in settings.py (after AuthenticationMiddleware)
  - Admin revoke endpoint sets Redis key (TTL 30 days)
  → Verify: Revoke device → next request from that device → 401

---

### Week 3: Core Business Logic (T9–T12)

---

- [ ] **T9**: Trip CRUD + lifecycle
  ```
  api/serializers/trip.py     # TripCreateSerializer, TripDetailSerializer
  api/views/trips.py          # TripViewSet with start/complete/cancel actions
  ```
  - `POST /api/trips/` → reads PricingConfig (via manager, with effective_from lookup) → snapshots prices + currency
  - `PATCH .../start/` → conductor, own trip, scheduled → in_progress
  - `PATCH .../complete/` → conductor, own trip, in_progress → completed, sets arrival_datetime
  - `PATCH .../cancel/` → office_staff/admin, scheduled → cancelled
  - QuerySet: `select_related('origin_office', 'destination_office', 'bus', 'conductor')`
  - QuerySet scoped by `OfficeScopePermission`

  Serializer validations:
  - origin ≠ destination (also DB constraint)
  - conductor.role == 'conductor'
  - bus.office == origin_office (can't use another office's bus)
  - bus.is_active == True

  → Verify: Trip price snapshot matches pricing_config for that date; currency = 'DZD'; wrong bus office → 400

---

- [ ] **T10**: Passenger Ticket CRUD
  ```
  api/serializers/passenger_ticket.py
  api/views/passenger_tickets.py
  ```
  - `POST /api/trips/{id}/passenger-tickets/` → conductor (in_progress), staff, admin
  - `PATCH /api/passenger-tickets/{id}/` → only on completed/cancelled trips
  - Composite unique: `(trip_id, ticket_number)`
  - Version incremented on update
  - Ticket number format: `PT-{trip.id}-{seq}` (auto-generated)
  - price must match trip.passenger_base_price (no custom pricing for passengers)

  → Verify: Create on completed trip by conductor → 403; duplicate ticket_number → 409; negative price → DB constraint error

---

- [ ] **T11**: Cargo Ticket CRUD + State Machine
  ```
  api/serializers/cargo_ticket.py
  api/views/cargo_tickets.py      # + override_status, receive, transition actions
  ```
  - Forward transitions via `CargoTicket.transition_to()` method
  - Admin override: any→any except 6 FORBIDDEN pairs, reason ≥10 chars
  - `PATCH .../receive/` → office_staff, pay_on_delivery → delivered
  - `PATCH .../status/` → office_staff, forward transitions only
  - `POST .../override-status/` → admin only

  → Verify: Forward transition ✅; backward by non-admin → 400; forbidden pair by admin → 400; reason < 10 → 400

---

- [ ] **T12**: Trip Expenses
  ```
  api/serializers/expense.py
  api/views/expenses.py
  ```
  - `POST /api/trips/{id}/expenses/` → conductor only, trip.status='in_progress'
  - `GET /api/trips/{id}/expenses/` → conductor (own), office_staff (scoped), admin
  - amount > 0 (DB constraint + serializer validation)
  → Verify: Expense on completed trip → 403; by office_staff → 403; negative amount → 400

---

### Week 4: Sync, Reports & Infrastructure (T13–T17)

---

- [ ] **T13**: Batch Sync endpoint
  ```python
  # api/views/sync.py — POST /api/sync/batch/
  @transaction.atomic
  def batch_sync(request):
      # 1. Check idempotency key (content hash) → "already_processed"
      # 2. SELECT FOR UPDATE on trip row
      # 3. Validate trip.status (if not in_progress → quarantine ALL)
      # 4. Batch-validate tickets (prefetch trip, not N+1)
      # 5. bulk_create with ignore_conflicts
      # 6. Create SyncLog entry
  ```
  Batch validation pattern (avoids N+1):
  ```python
  trip = Trip.objects.select_for_update().get(id=trip_id)
  # All tickets validated against same trip object — 1 query, not 50
  ```
  → Verify: Same content → "already_processed"; cancelled trip → ALL quarantined; concurrent cancel blocked by row lock

---

- [ ] **T14**: Quarantine Review
  ```
  api/views/admin_views.py
  ```
  - `GET /api/admin/quarantine/` → admin + office_staff (scoped)
  - `POST .../approve/` → admin only, checks `status='pending'`, creates tickets from `original_data`
  - `POST .../reject/` → admin only, notes required
  - Both idempotent (re-approve on approved → no-op 200)
  → Verify: Approve → tickets created; approve again → no duplicate; reject → marked

---

- [ ] **T15**: Reports
  ```
  api/views/reports.py
  ```
  - `GET /api/reports/daily/` → office-scoped, `prefetch_related`, ≤5 queries
  - `GET /api/reports/trip/{id}/` → trip + tickets + expenses in 3 queries
  - `GET /api/reports/route-analysis/` → admin only, aggregation query
  - All filtered by `created_at` date range (enabled by TimestampMixin)
  → Verify: 50 trips daily report → ≤5 SQL queries (`assertNumQueries`)

---

- [ ] **T16**: Excel Export (Celery task)
  ```python
  # api/tasks.py
  @shared_task(bind=True, time_limit=600, soft_time_limit=540,
               max_retries=0, rate_limit='2/m')
  def generate_excel_export(self, user_id, filters, max_rows=100_000):
      # write_only=True, iterator(chunk_size=1000)
      # .values_list() — select only needed columns, skip full ORM
      # cleanup_old_exports(user_id, keep_last=5)
  ```
  - `POST /api/reports/export/` → dispatches task, returns task_id
  - `GET /api/reports/export/{task_id}/` → polls status
  → Verify: 10K rows < 60s; 200K → error "use date filters"

---

- [ ] **T17**: Audit Middleware
  ```python
  # api/middleware/audit.py
  # Captures old/new values on POST/PUT/PATCH/DELETE
  # Uses signals (post_save, post_delete) for reliability
  # Falls back to middleware for request context (IP, user)
  ```
  - Logs: user, action, table, record_id, old_values (JSONB), new_values (JSONB), IP, created_at
  → Verify: Edit ticket → audit row with old_values showing previous state

---

### Week 5: Finishing (T18–T20)

---

- [ ] **T18**: Pricing Cache (Redis)
  ```python
  # PricingConfig post_save signal → invalidates cache
  # Trip creation reads from cache (key: f'pricing:{origin}:{dest}:{date}')
  # Cache TTL: 1 hour (fallback to DB on miss)
  ```
  → Verify: Update pricing → cache key deleted; next trip reads from DB → caches again

---

- [ ] **T19**: DB Security hardening (RunSQL migration)
  ```sql
  REVOKE DELETE, UPDATE ON audit_log FROM souigat_user;

  COMMENT ON COLUMN passenger_tickets.price IS
      'Integer in smallest unit. DZD: 2500 = 2,500 DA';
  COMMENT ON COLUMN trips.passenger_base_price IS
      'Snapshot from pricing_config. Immutable after trip starts.';
  COMMENT ON COLUMN cargo_tickets.price IS
      'Integer in smallest unit. Based on cargo_tier at trip creation.';
  ```
  → Verify: `DELETE FROM audit_log` → permission denied; `\d+ passenger_tickets` shows comments

---

- [ ] **T20**: Admin management endpoints
  ```
  api/views/admin_views.py — extend:
  ```
  - CRUD `/api/admin/users/` → admin only
  - CRUD `/api/admin/buses/` → admin only
  - CRUD `/api/admin/offices/` → admin only
  - CRUD `/api/admin/pricing/` → admin only (+ cache invalidation via signal)
  - `GET /api/admin/audit-log/` → admin only, filterable by date/user/table
  - `POST /api/admin/users/{id}/revoke-device/` → admin only
  - All use `select_related` for FK lookups
  → Verify: Non-admin → 403 on all admin endpoints

---

## on_delete Strategy Summary

| FK Relationship | on_delete | Rationale |
|----------------|-----------|-----------|
| Bus → Office | PROTECT | Can't delete office with buses |
| Trip → Office (x2) | PROTECT | Can't delete office with trips |
| Trip → Bus | PROTECT | Can't delete bus with trips |
| Trip → Conductor | PROTECT | Can't delete conductor with trips |
| Ticket → Trip | PROTECT | Can't delete trip with tickets |
| Ticket → created_by | PROTECT | Can't delete user who created tickets |
| Expense → Trip | CASCADE | Expenses belong to trip |
| Expense → created_by | PROTECT | Keep expense creator reference |
| AuditLog → User | SET_NULL | Keep audits after user deletion |
| Cargo → delivered_by | SET_NULL | Keep record, remove reference |
| Cargo → override_by | SET_NULL | Keep record, remove reference |
| Quarantine → reviewed_by | SET_NULL | Keep review, remove reviewer ref |
| SyncLog → User, Trip | PROTECT | Keep sync history |
| User → Office | SET_NULL | Reassign users before deleting office |

---

## File Summary

| Directory | Files | Purpose |
|-----------|-------|---------|
| `api/models/` | 13 files | 11 models + mixins + `__init__.py` |
| `api/serializers/` | 6 files | auth, trip, passenger, cargo, expense, quarantine |
| `api/views/` | 8+ files | auth, trips, tickets, expenses, sync, reports, admin, export |
| `api/middleware/` | 2 files | audit, device_revocation |
| `api/` | permissions.py | 4 RBAC classes |
| `api/` | tasks.py | Celery Excel export |
| `api/management/` | seed_data.py | Dev seed data |

---

## Verification Plan

### Automated
```bash
docker compose exec backend python manage.py test api -v2
docker compose exec backend python manage.py check --deploy
```

### 10 Manual Scenarios
1. **RBAC matrix**: Test all role+department combos
2. **Trip price freeze**: Create trip → update pricing → trip price unchanged
3. **Trip validation**: Same origin/dest → 400; wrong bus office → 400
4. **Cargo state machine**: All valid transitions + 6 forbidden + admin override
5. **Sync idempotency**: Same batch twice → "already_processed"
6. **Sync quarantine**: Cancelled trip → all tickets quarantined
7. **Audit immutability**: SQL DELETE on audit_log → denied
8. **Device revocation**: Revoke → immediate 401
9. **Temporal pricing**: Change pricing effective tomorrow → today's trip uses old price
10. **Soft delete**: Soft-delete office → trips still queryable via `all_objects`

### Git Commits
```
feat(models): 11 models + mixins + indexes + constraints (T1-T5)
feat(auth): JWT login + RBAC + device revocation (T6-T8)
feat(core): trip + tickets + cargo state machine + expenses (T9-T12)
feat(sync): batch sync + quarantine + reports + export (T13-T17)
feat(infra): pricing cache + DB security + admin CRUD (T18-T20)
```

# PLAN-mobile-dev — SOUIGAT Android Mobile Application (Phase 3)

**Date:** March 4, 2026 (v7 — API Contract Aligned)  
**Phase:** 3 (follows completed Backend + Web phases)  
**Project Type:** MOBILE — Native Android  
**Agent:** `mobile-developer`  
**Status:** ✅ Approved for Implementation  
**Audit Score:** 7.2 → 7.8 → 8.1 → 8.5 → 9.0/10

> **v7 Changelog:** API contract mismatch resolved — SyncWorker rewritten for per-item response format, `local_id` echo added to backend, §8.6 per-item key generation defined, `resume_from` clamp added, `markSyncedByBackendId` → `markSyncedByLocalId`, Docker port bound to localhost.

---

## 1. Overview

Build a native Android application for bus conductors to manage trips, create passenger/cargo tickets, record expenses, and sync data with the Django REST backend — all offline-first. This is the **primary client** for field operations; the web portal is secondary (admin/office use).

### Why Native Kotlin (Not Cross-Platform)

| Factor | Native Kotlin | React Native | Flutter |
|--------|--------------|--------------|---------|
| APK size | **~5-8 MB** | ~15-20 MB | ~15-20 MB |
| Offline SQLite | **Room (first-class)** | AsyncStorage/SQLite wrapper | sqflite (adequate) |
| Background sync | **WorkManager (native)** | Headless JS (fragile) | workmanager plugin |
| RAM on 1-2GB devices | **Excellent** | Heavy JS bridge | Good but larger footprint |
| Target users' devices | **Budget Android phones** | Needs more RAM | Needs more RAM |

> **Decision confirmed in architecture v3.1:** Native Android = smallest APK, best offline, ideal for budget phones.

---

## 2. Development Environment & Tools

### Hardware & Software

| Item | Details |
|------|---------|
| **OS** | Windows (user's machine) |
| **IDE** | Android Studio (latest stable — Ladybug or newer) |
| **Test Device** | Redmi Turbo 4 Pro (physical device via USB debugging, **MIUI — needs battery whitelist**) |
| **Emulator** | Android Studio AVD (for quick iteration) |
| **Backend (dev)** | `localhost:8000` via Docker Compose (existing setup) |
| **Backend (staging)** | Remote staging server (deployed March 1) |

### Required Android Studio Plugins

| Plugin | Purpose |
|--------|---------|
| Kotlin (built-in) | Primary language |
| Room Inspector | View local SQLite DB on device |
| Layout Inspector | Debug UI hierarchy |
| Network Inspector | Monitor API calls |
| Device File Explorer | Browse on-device files |

### SDK Configuration

```
compileSdk = 35         (Android 15 — latest SDK)
minSdk = 26             (Android 8.0 Oreo — WorkManager + EncryptedSharedPreferences)
targetSdk = 35          (Android 15)
Kotlin = 2.0+
Gradle = 8.x
AGP = 8.x
```

> **Why minSdk 26:** Enables `EncryptedSharedPreferences` (no fallback needed), efficient `WorkManager` APIs, modern `Room` features, and drops support for <2% of devices still on Android 5-7.

---

## 3. Tech Stack (Detailed)

### Core Architecture: MVVM + Clean Architecture

```
┌─────────────────────────────────────────────┐
│                  UI Layer                   │
│  Jetpack Compose + Material 3              │
│  Screens → ViewModels → StateFlow          │
├─────────────────────────────────────────────┤
│              Domain Layer                   │
│  UseCases (business rules)                 │
│  Repository Interfaces                     │
├─────────────────────────────────────────────┤
│               Data Layer                    │
│  Room (local)  ←→  Repository  ←→  Retrofit│
│  SQLite DB          impl         REST API  │
│                      ↓                      │
│              WorkManager (sync)             │
└─────────────────────────────────────────────┘
```

### Dependencies (Libraries)

| Category | Library | Version | Purpose |
|----------|---------|---------|---------|
| **UI** | Jetpack Compose + Material 3 | BOM 2024.x | Modern declarative UI |
| **Navigation** | Navigation Compose | 2.8+ | Screen routing + bottom nav |
| **DI** | Hilt (Dagger) | 2.51+ | Dependency injection (compile-time safe) |
| **Local DB** | Room | 2.6+ | SQLite ORM + offline storage |
| **Network** | Retrofit 2 + OkHttp 4 | 2.11+ / 4.12+ | REST API client + **certificate pinning** |
| **JSON** | Kotlinx Serialization | 1.7+ | JSON parsing (faster than Gson) |
| **Background** | WorkManager | 2.9+ | Reliable offline sync |
| **Auth/Security** | EncryptedSharedPreferences | 1.1+ | JWT + device UUID storage |
| **Images** | Coil 3 | 3.x | Image loading (receipts) |
| **Async** | Kotlin Coroutines + Flow | 1.9+ | Async operations |
| **Lifecycle** | Lifecycle + ViewModel | 2.8+ | MVVM pattern |
| **Logging** | Timber | 5.x | Debug logging (stripped in release) |
| **Crash Reporting** | Firebase Crashlytics | latest | **Field crash visibility** |
| **Testing** | JUnit 5 + Mockk + Turbine | latest | Unit + Flow testing |
| **Migration Testing** | Room Testing (`MigrationTestHelper`) | 2.6+ | **Migration verification** |
| **UI Testing** | Compose Test | BOM | UI/integration tests |

### Why These Choices

- **Compose over XML:** Less boilerplate, faster iteration, better state management. The Redmi Turbo 4 Pro has sufficient specs for Compose.
- **Hilt over Koin:** Compile-time DI validation prevents runtime crashes in the field — unacceptable during a trip.
- **kotlinx.serialization over Gson:** Native Kotlin, no reflection overhead on budget devices.
- **EncryptedSharedPreferences over Keystore directly:** Simpler API, still uses AndroidKeyStore under the hood.
- **Firebase Crashlytics:** *(NEW)* Zero visibility into field crashes is unacceptable for 8 remote conductor devices. Crashlytics provides real-time crash reports with stack traces.

---

## 4. Infrastructure

### Network Configuration

```kotlin
// BuildConfig-based URL switching
object ApiConfig {
    val BASE_URL = when (BuildConfig.BUILD_TYPE) {
        "debug"   -> "http://10.0.2.2:8000/api/"   // Emulator → host localhost
        "staging" -> "https://staging.souigat.dz/api/"
        "release" -> "https://api.souigat.dz/api/"
        else      -> "http://10.0.2.2:8000/api/"
    }
}
```

> For physical device testing against local backend: use `adb reverse tcp:8000 tcp:8000` so the phone reaches `localhost:8000`.

### SSL Certificate Pinning (AUDIT FIX #6 — v3 corrected)

> ⚠️ **Why:** Conductors use public WiFi at bus depots — MITM attacks can intercept JWT tokens and financial data.

> 🔴 **CRITICAL:** Do NOT use placeholder pin values. The app will throw `SSLPeerUnverifiedException` on every API call if pins don't match the real server certificate.

**Step 1 — Extract real SPKI pins (mandatory before staging build):**

```bash
# Extract the INTERMEDIATE CA pin (NOT the leaf — intermediate survives cert renewal)
openssl s_client -connect staging.souigat.dz:443 -servername staging.souigat.dz < /dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | base64

# Output example: YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=
# Also extract the backup pin (Let's Encrypt alternate root):
# Use the second certificate in the chain from: openssl s_client -showcerts ...
```

**Step 2 — Configure OkHttp with real pins:**

```kotlin
// In NetworkModule.kt — ONLY for staging + release builds
// Pin the INTERMEDIATE CA for rotation resilience (leaf certs change on renewal)
val certificatePinner = CertificatePinner.Builder()
    .add("api.souigat.dz",
        "sha256/<REAL_INTERMEDIATE_PIN_FROM_STEP_1>",     // Current intermediate CA
        "sha256/<BACKUP_INTERMEDIATE_PIN_FROM_STEP_1>"    // Backup intermediate CA
    )
    .add("staging.souigat.dz",
        "sha256/<REAL_STAGING_PIN_FROM_STEP_1>",
        "sha256/<BACKUP_STAGING_PIN_FROM_STEP_1>"
    )
    .build()

val okHttpClient = OkHttpClient.Builder()
    .apply {
        // ONLY pin in staging/release — debug uses localhost without TLS
        if (BuildConfig.BUILD_TYPE != "debug") {
            certificatePinner(certificatePinner)
        }
    }
    .addInterceptor(authInterceptor)
    .authenticator(tokenRefreshAuthenticator)
    .build()
```

> **Pin rotation:** When server certificates rotate, update pins and push new APK. Two pins (current + backup intermediate) provide a rotation window. For 8 sideloaded devices, this is manageable.

> **Pre-staging checklist item:** "Run `openssl` command above, paste real pin hashes into `NetworkModule.kt`, verify staging APK can reach `staging.souigat.dz`" — this is a gate for T30.4.

#### Certificate Pin Rotation SLA (v5 — operational)

> 🔴 **This is not optional.** Cert pinning creates an ongoing APK update obligation. If pins break, ALL 8 phones lose connectivity simultaneously.

| Item | Detail |
|------|--------|
| **Rotation frequency** | Let's Encrypt intermediates rotate every ~3-6 months (unpredictable) |
| **Detection** | Set calendar reminder for Month 2 to re-extract and verify pins |
| **Impact of missed rotation** | `SSLPeerUnverifiedException` on every API call → zero connectivity on all 8 devices |
| **Rotation process** | Extract new pins → build staging APK → test on 1 device → build release APK → distribute to all 8 |
| **Distribution channel** | **WhatsApp group** (send APK file directly) — decided before first sideload |
| **Rollback** | If new APK has bugs, old APK with old pins still works until cert actually rotates |

### Build Variants

| Variant | API URL | Debug Tools | ProGuard | Cert Pinning |
|---------|---------|-------------|----------|-------------|
| `debug` | localhost:8000 | Timber, NetworkInspector | Off | **Off** (local dev) |
| `staging` | staging server | Timber, Crashlytics | On (debug mappings) | **On** |
| `release` | production server | Crashlytics only | Full R8 | **On** |

### Signing & Distribution

| Phase | Method |
|-------|---------|
| **Development** | USB debug install via Android Studio |
| **Staging/Testing** | Direct APK sideloading (generated `staging` APK) |
| **Production** | Direct APK sideload via WhatsApp group |
| **Updates** | New APK sent via WhatsApp group → conductors install over existing |

> MVP distribution: **Direct APK sideload** to 8 conductor phones. No Play Store needed.

#### 🔴 Keystore Management (v5 — CRITICAL, loss = data wipe on all 8 devices)

> **If the signing keystore is lost or password forgotten, Android refuses to install a new APK over the old one.** The only fix is full uninstall → Room database wiped → all unsynced tickets permanently lost. This is irreversible.

```bash
# Step 1: Generate signing keystore ONCE (Day 1, before first release build)
keytool -genkey -v -keystore souigat-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias souigat -storepass <STRONG_PASSWORD> -keypass <STRONG_PASSWORD>
```

**Mandatory backup protocol (do IMMEDIATELY after generation):**

| Backup Location | Detail |
|----------------|--------|
| **Cloud** | Upload `souigat-release.jks` to Bitwarden / 1Password (encrypted vault) |
| **Physical** | Copy to USB drive stored in office safe/locked drawer |
| **Document** | Record in password manager: keystore path, key alias (`souigat`), store password, key password |

**Rules:**
- ❌ **NEVER regenerate** the keystore — treat as company asset
- ❌ **NEVER delete** any backup
- ❌ **NEVER commit** to git (add `*.jks` to `.gitignore`)
- ✅ **Verify** backup: restore from cloud backup, sign a test APK, install over existing on test device

#### APK Update Distribution Protocol

| Step | Action |
|------|--------|
| 1 | Build release APK: `./gradlew assembleRelease` |
| 2 | Test on 1 conductor device (verify update installs over old, data preserved) |
| 3 | Send APK via **WhatsApp group** “SOUIGAT Conductors” |
| 4 | Conductors tap APK → "Install from unknown source" → install over existing |
| 5 | Verify: open app → previous login session + unsynced data still present |

> **Pre-first-sideload gate:** Create WhatsApp group, test APK delivery, confirm all 8 conductors can install from unknown sources.

### Backend Fix Required: Celery Export Shared Volume (AUDIT FIX #3)

> ⚠️ **Backend issue:** Celery worker writes exports to its own container filesystem. Django API container cannot read them → `FileNotFoundError` 100% of the time in Docker.

```yaml
# docker-compose.yml — add shared volume
volumes:
  exports_data:

services:
  backend:
    volumes:
      - exports_data:/app/exports
  celery:
    volumes:
      - exports_data:/app/exports
```

```python
# tasks.py — use shared path
path = f'/app/exports/{user_id}/{int(time.time())}.xlsx'
```

> This is a 5-minute backend fix, not a mobile task — but it must be done before mobile conductors trigger exports.

---

## 5. Project File Structure

```
mobile/
├── app/
│   ├── build.gradle.kts            # App-level build config
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/souigat/mobile/
│       │   │   ├── SouigatApp.kt                    # Application class (Hilt entry)
│       │   │   ├── MainActivity.kt                  # Single-activity Compose host
│       │   │   │
│       │   │   ├── di/                              # Dependency Injection
│       │   │   │   ├── AppModule.kt                 # Singleton providers (+ DeviceId)
│       │   │   │   ├── DatabaseModule.kt            # Room + DAOs + Migrations
│       │   │   │   └── NetworkModule.kt             # Retrofit + OkHttp + CertPinner
│       │   │   │
│       │   │   ├── data/                            # Data Layer
│       │   │   │   ├── local/                       # Room Database
│       │   │   │   │   ├── SouigatDatabase.kt       # RoomDatabase + Migration objects
│       │   │   │   │   ├── migration/               # ⚡ AUDIT FIX: Explicit migrations
│       │   │   │   │   │   └── Migrations.kt        # All Migration(n, n+1) objects
│       │   │   │   │   ├── dao/                     # Data Access Objects
│       │   │   │   │   │   ├── TripDao.kt
│       │   │   │   │   │   ├── PassengerTicketDao.kt
│       │   │   │   │   │   ├── CargoTicketDao.kt
│       │   │   │   │   │   ├── ExpenseDao.kt
│       │   │   │   │   │   └── SyncQueueDao.kt
│       │   │   │   │   └── entity/                  # Room Entities
│       │   │   │   │       ├── TripEntity.kt
│       │   │   │   │       ├── PassengerTicketEntity.kt
│       │   │   │   │       ├── CargoTicketEntity.kt
│       │   │   │   │       ├── ExpenseEntity.kt
│       │   │   │   │       └── SyncQueueEntity.kt   # ⚡ Has status enum
│       │   │   │   │
│       │   │   │   ├── remote/                      # Retrofit API
│       │   │   │   │   ├── api/
│       │   │   │   │   │   ├── AuthApi.kt
│       │   │   │   │   │   ├── TripApi.kt
│       │   │   │   │   │   ├── TicketApi.kt
│       │   │   │   │   │   ├── ExpenseApi.kt
│       │   │   │   │   │   └── SyncApi.kt
│       │   │   │   │   ├── dto/
│       │   │   │   │   │   ├── LoginRequest.kt
│       │   │   │   │   │   ├── LoginResponse.kt
│       │   │   │   │   │   ├── TripDto.kt
│       │   │   │   │   │   ├── TicketDto.kt
│       │   │   │   │   │   └── SyncBatchRequest.kt
│       │   │   │   │   └── interceptor/
│       │   │   │   │       ├── AuthInterceptor.kt   # JWT injection
│       │   │   │   │       └── TokenRefreshAuthenticator.kt
│       │   │   │   │
│       │   │   │   └── repository/
│       │   │   │       ├── AuthRepositoryImpl.kt
│       │   │   │       ├── TripRepositoryImpl.kt
│       │   │   │       ├── TicketRepositoryImpl.kt
│       │   │   │       ├── ExpenseRepositoryImpl.kt
│       │   │   │       └── SyncRepositoryImpl.kt
│       │   │   │
│       │   │   ├── domain/                          # Domain Layer
│       │   │   │   ├── model/                       # Domain Models (@Stable annotated)
│       │   │   │   │   ├── Trip.kt
│       │   │   │   │   ├── PassengerTicket.kt
│       │   │   │   │   ├── CargoTicket.kt
│       │   │   │   │   ├── Expense.kt
│       │   │   │   │   ├── User.kt
│       │   │   │   │   └── SyncStatus.kt            # ⚡ PENDING/SYNCING/SYNCED/QUARANTINED/FAILED
│       │   │   │   ├── repository/
│       │   │   │   │   ├── AuthRepository.kt
│       │   │   │   │   ├── TripRepository.kt
│       │   │   │   │   ├── TicketRepository.kt
│       │   │   │   │   └── ExpenseRepository.kt
│       │   │   │   └── usecase/
│       │   │   │       ├── LoginUseCase.kt
│       │   │   │       ├── GetTripsUseCase.kt
│       │   │   │       ├── StartTripUseCase.kt
│       │   │   │       ├── CreateTicketUseCase.kt
│       │   │   │       ├── CreateExpenseUseCase.kt
│       │   │   │       └── SyncDataUseCase.kt
│       │   │   │
│       │   │   ├── ui/                              # UI Layer (Compose)
│       │   │   │   ├── theme/
│       │   │   │   │   ├── Theme.kt
│       │   │   │   │   ├── Color.kt
│       │   │   │   │   └── Type.kt                  # Inter font
│       │   │   │   ├── navigation/
│       │   │   │   │   ├── AppNavGraph.kt
│       │   │   │   │   └── BottomNavBar.kt
│       │   │   │   ├── screens/
│       │   │   │   │   ├── login/
│       │   │   │   │   │   ├── LoginScreen.kt
│       │   │   │   │   │   └── LoginViewModel.kt
│       │   │   │   │   ├── dashboard/
│       │   │   │   │   │   ├── DashboardScreen.kt
│       │   │   │   │   │   └── DashboardViewModel.kt
│       │   │   │   │   ├── trip/
│       │   │   │   │   │   ├── TripListScreen.kt
│       │   │   │   │   │   ├── TripDetailScreen.kt
│       │   │   │   │   │   └── TripViewModel.kt
│       │   │   │   │   ├── ticket/
│       │   │   │   │   │   ├── CreateTicketScreen.kt
│       │   │   │   │   │   └── TicketViewModel.kt
│       │   │   │   │   ├── expense/
│       │   │   │   │   │   ├── CreateExpenseScreen.kt
│       │   │   │   │   │   ├── ExpenseListScreen.kt
│       │   │   │   │   │   └── ExpenseViewModel.kt
│       │   │   │   │   └── profile/
│       │   │   │   │       ├── ProfileScreen.kt     # Sync status + MIUI guide
│       │   │   │   │       └── ProfileViewModel.kt
│       │   │   │   └── components/
│       │   │   │       ├── StatCard.kt
│       │   │   │       ├── ActivityItem.kt
│       │   │   │       ├── RouteInfoCard.kt
│       │   │   │       ├── SyncStatusBadge.kt
│       │   │   │       ├── QuarantineWarningBanner.kt  # ⚡ Non-dismissable quarantine alert
│       │   │   │       └── LoadingButton.kt
│       │   │   │
│       │   │   ├── sync/                            # Background Sync
│       │   │   │   ├── SyncWorker.kt                # WorkManager worker (push)
│       │   │   │   ├── TripStatusPullWorker.kt      # ⚡ AUDIT FIX: Server → device pull
│       │   │   │   ├── SyncForegroundService.kt     # ⚡ MIUI fallback for active trips
│       │   │   │   ├── SyncManager.kt               # Sync orchestration
│       │   │   │   └── IdempotencyKeyGenerator.kt   # Content-hash (full SHA-256)
│       │   │   │
│       │   │   └── util/
│       │   │       ├── DeviceIdProvider.kt          # ⚡ AUDIT FIX: UUID, not Android ID
│       │   │       ├── NetworkMonitor.kt
│       │   │       ├── DateUtils.kt
│       │   │       ├── CurrencyFormatter.kt
│       │   │       └── Constants.kt
│       │   │
│       │   └── res/
│       │       ├── values/
│       │       │   ├── strings.xml
│       │       │   └── themes.xml
│       │       ├── xml/
│       │       │   └── network_security_config.xml  # ⚡ Cleartext for debug only
│       │       └── drawable/
│       │
│       ├── test/                                    # Unit tests
│       │   └── java/com/souigat/mobile/
│       │       ├── data/repository/
│       │       ├── domain/usecase/
│       │       └── sync/
│       │           └── IdempotencyKeyGeneratorTest.kt
│       │
│       └── androidTest/                             # Instrumented tests
│           └── java/com/souigat/mobile/
│               ├── data/local/
│               │   ├── dao/
│               │   └── migration/
│               │       └── MigrationTest.kt         # ⚡ AUDIT FIX: Migration verification
│               └── ui/screens/
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── local.properties
```

---

## 6. UI Design System

### Design Language: Material 3 (Material You)

| Token | Value | Source |
|-------|-------|--------|
| **Primary** | `#2563EB` (Blue 600) | From mockups |
| **On Primary** | `#FFFFFF` | White on blue |
| **Surface** | `#FFFFFF` (light) / `#1F2937` (dark) | Mockup card-light/dark |
| **Background** | `#F3F4F6` (light) / `#111827` (dark) | Mockup bg |
| **Success** | `#10B981` (Emerald) | Revenue, synced |
| **Error** | `#EF4444` (Red) | Expenses, errors |
| **Warning** | `#F59E0B` (Amber) | Quarantine, stale |
| **Font** | Inter (Google Fonts) | Mockup font |
| **Touch targets** | 48dp minimum | Android guidelines |

### Compose Performance Rules

> ⚡ **Audit optimization:** Prevent full-list recomposition on ticket lists.

- All domain models annotated with `@Stable`
- `LazyColumn` uses `key = { item.id }` for stable identity
- `renderItem` lambdas use `@Composable` with `remember`
- `StateFlow<List<T>>` emits only on actual data change (not on every insert)

### Screen Map

```
Login ─────────────────────────────────────── (no bottom nav)
   │
   ▼
Bottom Nav ──┬── Dashboard (Home)
             │     • Greeting header + sync badge
             │     • Current route card (blue gradient)
             │     • Stats grid (revenue, tickets, expenses)
             │     • Recent activity list (LazyColumn + key)
             │     • ⚡ QuarantineWarningBanner (if quarantined tickets exist)
             │     • FAB (+) → Create Ticket
             │
             ├── History
             │     • Past trips list
             • Per-trip summary
             │
             ├── Expenses
             │     • Expense list for current trip
             │     • Create expense (amount, category, description)
             │
             └── Profile
                   • User info
                   • Sync status + manual sync button
                   • ⚡ Quarantined tickets count + admin contact
                   • WiFi-only toggle
                   • ⚡ MIUI battery optimization guide
                   • App version
```

---

## 7. MVP Task Breakdown

### Phase 3.0: Project Scaffold (Day 1-2)

- [ ] **T30.1**: Initialize Android project in `mobile/` with Kotlin DSL, Compose, Hilt
  - Set up Firebase project in Firebase Console → download `google-services.json` → place in `app/`
  - Crashlytics with GMS availability check (see §8.7 for MIUI fallback)
- [ ] **T30.2**: Configure build variants (debug/staging/release) + **keystore generation + backup** (see §4) + `network_security_config.xml`
- [ ] **T30.3**: Set up Room database with **explicit migration protocol** (see §8.1) + **TypeConverters** (§8.1)
- [ ] **T30.4**: Set up Retrofit + OkHttp + **CertificatePinner** (staging/release only — extract real pins first)
- [ ] **T30.5**: Create Material 3 theme (colors, typography from mockups)
- [ ] **T30.6**: Create navigation graph + bottom nav bar
- → **Verify**: `./gradlew assembleDebug` succeeds, app launches to empty shell on Redmi Turbo 4 Pro; keystore backed up to 2 locations; `google-services.json` present

### Phase 3.1: Authentication (Day 3-4)

- [ ] **T31.1**: Login screen (phone + password form, Material 3 design)
- [ ] **T31.2**: Auth API integration (Retrofit → `POST /api/auth/login/`)
- [ ] **T31.3**: JWT storage in EncryptedSharedPreferences (access + refresh tokens)
- [ ] **T31.4**: Auth interceptor (inject `Authorization: Bearer` header)
- [ ] **T31.5**: Token refresh with Mutex (thread-safe, `platform=mobile` → no rotation)
- [ ] **T31.6**: ⚡ **Device UUID binding** — `UUID.randomUUID()` stored in EncryptedSharedPreferences on first launch (NOT Android ID)
- [ ] **T31.7**: Auto-logout on 401 (revoked device or expired session)
- → **Verify**: Login with conductor account → dashboard loads; expired token → auto-refresh; revoked device → login screen

### Phase 3.2: Trip Dashboard (Day 5-7)

- [ ] **T32.1**: Dashboard screen (replicate mockup: greeting, route card, stats grid, activity)
- [ ] **T32.2**: Trip list — conductor's assigned trips from Room (offline) + API (online)
- [ ] **T32.3**: Trip detail screen (passengers, cargo, expenses, totals)
- [ ] **T32.4**: Start trip action (`PATCH /api/trips/{id}/start/`) — status → in_progress
- [ ] **T32.5**: Complete trip action (`PATCH /api/trips/{id}/complete/`) — status → completed
- [ ] **T32.6**: Stale data warning (trip data >4 hours old → yellow banner)
- [ ] **T32.7**: ⚡ **TripStatusPullWorker** — periodic pull of conductor's active trip status from server (see §8.4)
- → **Verify**: View assigned trips; start trip → status changes; trip cancelled on server → conductor notified on next pull

### Phase 3.3: Ticket Creation (Day 8-11)

- [ ] **T33.1**: Create Ticket screen with Passenger/Cargo segmented tabs (from mockup)
- [ ] **T33.2**: Passenger ticket form: origin, destination, name, seat (optional), price (frozen from trip)
- [ ] **T33.3**: Cargo ticket form: sender, receiver, phones, tier (small/medium/large), description, payment type
- [ ] **T33.4**: Auto-generate ticket number: `PT-{TRIP_ID}-{SEQ}` / `CT-{TRIP_ID}-{SEQ}`
- [ ] **T33.5**: Save to Room first (offline), mark `syncStatus = PENDING`
- [ ] **T33.6**: Ticket creation restricted to `in_progress` trips only
- [ ] **T33.7**: Recent tickets list on dashboard (`LazyColumn` with `key = { ticket.id }`, `@Stable` models)
- → **Verify**: Create passenger ticket offline → saved in Room; create cargo ticket → saved; ticket number auto-generated; blocked on non-in_progress trip

### Phase 3.4: Expense Recording (Day 12-13)

- [ ] **T34.1**: Create Expense screen (from mockup: amount input, category grid, description)
- [ ] **T34.2**: Categories: Fuel, Food, Maintenance, Tolls, Other (from mockup)
- [ ] **T34.3**: Save to Room first (offline), mark `syncStatus = PENDING`
- [ ] **T34.4**: Expense restricted to `in_progress` trips only
- [ ] **T34.5**: Expense list screen for current trip
- → **Verify**: Record expense offline → saved in Room; category icons match; blocked on completed trip

### Phase 3.5: Background Sync (Day 14-18)

- [ ] **T35.1**: ⚡ **SyncManager** with dual-trigger strategy (see §8.3):
  - `OneTimeWorkRequest` with `NetworkType.CONNECTED` constraint (fires on reconnect — **0 delay**)
  - `PeriodicWorkRequest` every 15 minutes as fallback (**NOT 60s — OS minimum is 15min**)
- [ ] **T35.2**: ⚡ **Idempotency key** — full SHA-256 hex (64 chars, **no truncation**)
- [ ] **T35.3**: Batch sync: `POST /api/sync/batch/` with tickets + expenses
- [ ] **T35.4**: ⚡ **Partial sync response handler** (see §8.4 for full implementation):
  - Backend returns per-item results in `items[]` — each has `status` = `accepted`/`quarantined`/`duplicate`
  - Extract accepted IDs: `items.filter { it.status == "accepted" }.mapNotNull { it.id }`
  - Map quarantined items back to local Room IDs via batch index → mark `syncStatus = QUARANTINED`
  - `duplicate` = already processed per-item, no action needed (replaces old `already_processed` branch)
  - Use `last_processed_index` for interrupted sync resume
  - **NEVER mark all as SYNCED** — only items with `status == "accepted"` get marked SYNCED
- [ ] **T35.5**: Sync preferences: WiFi-only toggle, manual sync button
- [ ] **T35.6**: NetworkMonitor — observe connectivity via ConnectivityManager callback
- [ ] **T35.7**: ⚡ **Sync status UI**:
  - Dashboard badge: "Synced" / "X pending" / "⚠ X quarantined"
  - **QuarantineWarningBanner**: non-dismissable if quarantined tickets exist, tells conductor to contact admin
  - Profile screen: detailed sync stats + quarantine count
- [ ] **T35.8**: Retry with exponential backoff (1m, 2m, 4m — max 3 retries), then mark `FAILED`
- [ ] **T35.9**: ⚡ **SyncForegroundService** — MIUI/Xiaomi fallback:
  - Starts as foreground service with notification when trip is `in_progress`
  - Keeps app alive during active trips on Xiaomi devices
  - Uses `START_NOT_STICKY` — if killed, WorkManager handles retries (service is keep-alive only)
  - Profile screen guides user to whitelist SOUIGAT from battery optimization
- → **Verify**: Tickets sync on reconnect (0 delay); same batch retried → verify quarantined items remain QUARANTINED (not SYNCED); kill app during sync → retry → quarantine preserved; WiFi-only toggle respected; MIUI: sync completes during active trip

### Phase 3.X: Testing & Polish (Day 19-21)

- [ ] **T3X.1**: Unit tests (UseCases, Repository, IdempotencyKeyGenerator, DeviceIdProvider)
- [ ] **T3X.2**: ⚡ **Room migration tests** using `MigrationTestHelper` — verify every `Migration(n, n+1)` preserves data
- [ ] **T3X.3**: Room DAO instrumented tests (insert, query, sync status filter including QUARANTINED)
- [ ] **T3X.4**: Full flow test on Redmi Turbo 4 Pro: login → start trip → create tickets → expense → complete trip → sync
- [ ] **T3X.5**: Airplane mode test: full workflow offline → reconnect → auto-sync
- [ ] **T3X.6**: ⚡ **Partial sync test**: manually trigger quarantine response → verify `QUARANTINED` status + warning banner
- [ ] **T3X.7**: ⚡ **MIUI battery kill test**: start trip → background app → wait 5 min → verify foreground service kept sync alive
- [ ] **T3X.8**: Release APK build: `./gradlew assembleRelease`
- → **Verify**: All tests pass; APK < 10MB; full offline workflow verified on physical device; migration tests pass

---

## 8. Audit Fixes — Technical Details

### 8.1 🔴 Room Migration Protocol (Fix #1)

> **Problem:** No migration strategy → crash-loop or silent data loss on first app update.

**Rule: NEVER use `fallbackToDestructiveMigration()` in any build variant.**

```kotlin
// data/local/Converters.kt — REQUIRED for SyncStatus enum storage
// Without this, Room stores enums as ordinal integers (0, 1, 2...)
// but DAO queries use string literals ('PENDING', 'QUARANTINED')
// → all queries silently return 0 results → sync system appears to work but never syncs
class Converters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}

// SouigatDatabase.kt
@Database(
    entities = [TripEntity::class, PassengerTicketEntity::class, ...],
    version = 1,  // Increment on EVERY schema change
    exportSchema = true  // Room exports JSON schema for migration testing
)
@TypeConverters(Converters::class)  // ⚡ v4 FIX: Register enum converters
abstract class SouigatDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    // ... other DAOs
}

// DatabaseModule.kt (Hilt)
@Provides @Singleton
fun provideDatabase(@ApplicationContext context: Context): SouigatDatabase {
    return Room.databaseBuilder(context, SouigatDatabase::class.java, "souigat.db")
        .addMigrations(*Migrations.ALL)  // Explicit migrations only
        // NO .fallbackToDestructiveMigration() — EVER
        .build()
}

// migration/Migrations.kt — every schema change gets a Migration object
object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE passenger_tickets ADD COLUMN seat_number TEXT")
        }
    }
    // Add future migrations here
    val ALL = arrayOf(MIGRATION_1_2)
}
```

**Migration test (mandatory for every migration):**
```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SouigatDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesPendingTickets() {
        // Create DB at version 1 with unsynced tickets
        helper.createDatabase("souigat.db", 1).apply {
            execSQL("INSERT INTO passenger_tickets (...) VALUES (...)")
            close()
        }
        // Migrate to version 2 — must NOT crash, data preserved
        val db = helper.runMigrationsAndValidate("souigat.db", 2, true, Migrations.MIGRATION_1_2)
        val cursor = db.query("SELECT * FROM passenger_tickets")
        assertThat(cursor.count).isGreaterThan(0)  // Data survived!
    }
}
```

### 8.2 🔴 Device UUID (Fix #2 — replaces Android ID)

> **Problem:** `Android ID` is unreliable — changes on factory reset, scoped per app+signing key on Android 10+, sometimes null on OEM ROMs.

```kotlin
// util/DeviceIdProvider.kt
@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ⚡ v6 FIX: Use non-deprecated MasterKey.Builder (replaces MasterKeys.getOrCreate)
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "souigat_device",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getDeviceId(): String {
        return prefs.getString("device_uuid", null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString("device_uuid", id).apply()
            id
        }
    }
}
```

- Generated once on first launch, persists across app updates
- Stored in EncryptedSharedPreferences (not accessible to other apps)
- Survives ROM changes and is unique per install
- On reinstall: new UUID → admin must re-bind (acceptable for 8 conductors)

### 8.3 🔴 Sync Architecture (Fix #4 — replaces "60s poll")

> **Problem:** WorkManager minimum periodic interval is 15 minutes, NOT 60 seconds.

**Dual-trigger strategy:**

```kotlin
// SyncManager.kt
class SyncManager @Inject constructor(
    private val workManager: WorkManager
) {
    // TRIGGER 1: On connectivity change — fires immediately when WiFi/data reconnects
    fun scheduleOnConnectivitySync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            "sync_on_connect",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    // TRIGGER 2: Periodic fallback — minimum 15 min (OS constraint)
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "sync_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // TRIGGER 3: MIUI foreground service — for active trips on Xiaomi devices
    fun startForegroundSyncService(context: Context) {
        val intent = Intent(context, SyncForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopForegroundSyncService(context: Context) {
        context.stopService(Intent(context, SyncForegroundService::class.java))
    }
}
```

**Result:** Sync fires on reconnect (~0 delay) + every 15 min as fallback + foreground service on MIUI.

#### SyncForegroundService Contract (v3 — fully specified)

> ⚠️ **Android 8+ requirement:** Foreground service MUST post a notification within 5 seconds of `onCreate()` or the system throws `ForegroundServiceDidNotStartInTimeException`.

```kotlin
// sync/SyncForegroundService.kt
class SyncForegroundService : Service() {

    // LIFECYCLE:
    // START → when conductor taps "Start Trip" (TripViewModel calls SyncManager.startForegroundSyncService)
    // STOP  → when trip is completed OR conductor manually syncs AND no PENDING items remain
    //         (TripViewModel calls SyncManager.stopForegroundSyncService)

    override fun onCreate() {
        super.onCreate()
        // Create notification channel (required Android 8+)
        val channel = NotificationChannel(
            "sync_active_trip",
            "Active Trip Sync",
            NotificationManager.IMPORTANCE_LOW  // No sound, just persistent icon
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ⚡ v4 FIX: Handle null intent (OS restart after kill)
        // With START_NOT_STICKY this shouldn't happen, but defensive check
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // MUST post notification within 5 seconds
        val notification = NotificationCompat.Builder(this, "sync_active_trip")
            .setContentTitle("SOUIGAT — Active Trip")
            .setContentText("Syncing trip data in background")
            .setSmallIcon(R.drawable.ic_sync)  // Must be a valid drawable
            .setOngoing(true)    // Cannot be swiped away
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // WORK: Does NOT run its own network loop.
        // WorkManager does the actual sync; this service just keeps the process alive
        // so MIUI doesn't kill it during an active trip.
        return START_NOT_STICKY  // ⚡ v4 FIX: Don't restart if killed — WorkManager handles retries
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
```

**AndroidManifest.xml addition:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name=".sync.SyncForegroundService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

> **Note:** Foreground service is a *supplement* to battery whitelisting, not a replacement. The Profile screen must still guide conductors to whitelist SOUIGAT from MIUI battery optimization.

### 8.4 🟠 SyncQueueEntity with Status Enum (Fix #5)

> **Problem:** No `QUARANTINED` status → quarantined tickets invisible to conductor.

```kotlin
// entity/SyncQueueEntity.kt
enum class SyncStatus { PENDING, SYNCING, SYNCED, QUARANTINED, FAILED }

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticketId: Long,
    val ticketType: String,    // "passenger" or "cargo" or "expense"
    val tripId: Long,
    val status: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null
)

// dao/SyncQueueDao.kt
@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' OR status = 'FAILED'")
    suspend fun getPendingItems(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'QUARANTINED'")
    fun getQuarantinedCount(): Flow<Int>  // Observed by UI for warning banner

    @Query("UPDATE sync_queue SET status = 'SYNCED', syncedAt = :syncedAt WHERE ticketId IN (:ids)")
    suspend fun markSynced(ids: List<Long>, syncedAt: Long)

    @Query("UPDATE sync_queue SET status = 'QUARANTINED' WHERE ticketId IN (:ids)")
    suspend fun markQuarantined(ids: List<Long>)

    @Query("UPDATE sync_queue SET status = 'FAILED', retryCount = retryCount + 1, lastError = :error WHERE ticketId IN (:ids)")
    suspend fun markFailed(ids: List<Long>, error: String)
}
```

**Partial response handler in SyncWorker (v7 — rewritten for per-item backend response):**

> ⚠️ **CRITICAL CONTRACT**: The backend `POST /api/sync/batch/` does NOT return a top-level `status` field.
> It returns per-item results. The `local_id` field is echoed back from the request for Room ID mapping.
> ```json
> {
>   "sync_log_trip": 1,
>   "accepted": 3,         // integer COUNT, not a list of IDs
>   "quarantined": 1,      // integer COUNT, not a list of IDs
>   "duplicates": 0,
>   "last_processed_index": 3,
>   "items": [
>     { "index": 0, "status": "accepted", "id": 42, "local_id": 7 },
>     { "index": 1, "status": "accepted", "id": 43, "local_id": 8 },
>     { "index": 2, "status": "quarantined", "reason": "Bus is at full capacity (50 seats).", "local_id": 9 },
>     { "index": 3, "status": "duplicate", "key": "sync-1-abc..." }
>   ]
> }
> ```

```kotlin
// --- DTOs matching actual backend response ---
data class SyncItemResult(
    val index: Int,
    val status: String,       // "accepted", "quarantined", "duplicate"
    val id: Long? = null,     // Server-side backend ID (for logs)
    @SerialName("local_id")
    val localId: Long? = null, // Echoed Room entity PK — THIS is what you use for Room updates
    val key: String? = null,
    val reason: String? = null
)

data class SyncBatchResponse(
    @SerialName("sync_log_trip") val syncLogTrip: Long,
    val accepted: Int,              // COUNT — not a list
    val quarantined: Int,           // COUNT — not a list
    val duplicates: Int,
    @SerialName("last_processed_index") val lastProcessedIndex: Int,
    val items: List<SyncItemResult>
)

// --- SyncItem sent TO the backend (includes local Room PK) ---
data class SyncItem(
    val type: String,                // "passenger_ticket", "cargo_ticket", "expense"
    @SerialName("idempotency_key")
    val idempotencyKey: String,
    val payload: Map<String, Any>,
    @SerialName("local_id")
    val localId: Long               // Room entity PK — echoed back in response
)

// --- In SyncWorker.doWork() ---
val response = syncApi.batchSync(batchRequest)

// Extract accepted LOCAL IDs from per-item results (echoed back by backend)
val acceptedLocalIds = response.items
    .filter { it.status == "accepted" }
    .mapNotNull { it.localId }   // ← uses echoed local_id, NOT server id

// Extract quarantined LOCAL IDs
val quarantinedLocalIds = response.items
    .filter { it.status == "quarantined" }
    .mapNotNull { it.localId }

// "duplicate" items = already synced in a previous batch, no action needed

// Mark results in Room (chunked to stay under SQLite 999-variable limit)
acceptedLocalIds.chunked(500).forEach { chunk ->
    syncQueueDao.markSyncedByLocalId(chunk, System.currentTimeMillis())
}
quarantinedLocalIds.chunked(500).forEach { chunk ->
    syncQueueDao.markQuarantined(chunk)
}

// If sync was interrupted, use last_processed_index for resume
if (response.lastProcessedIndex < batchItems.size - 1) {
    // Partial batch — save resume point for next sync attempt
    syncPrefs.saveResumeFrom(response.lastProcessedIndex + 1)
}
```

> **Why no `already_processed` branch?** The backend uses per-item idempotency keys.
> When a batch is retried, each previously-processed item returns `"status": "duplicate"`
> in the `items[]` array. There is no batch-level `already_processed` event.
> The `GET /api/sync/log/{key}/` endpoint exists for admin debugging, not for the
> SyncWorker — the per-item `duplicate` status provides all needed information.

> **Why chunked(500)?** SQLite's `IN (:ids)` clause has a 999-variable limit. With batches up to 50 tickets this is unlikely to hit, but the safeguard costs nothing and prevents a crash if batch sizes grow.

**DAO update — local-ID queries for per-item sync results:**
```kotlin
// SyncQueueDao — uses echoed local Room IDs, NOT backend server IDs
@Query("UPDATE sync_queue SET status = 'SYNCED', syncedAt = :syncedAt WHERE id IN (:localIds)")
suspend fun markSyncedByLocalId(localIds: List<Long>, syncedAt: Long)

@Query("UPDATE sync_queue SET status = 'QUARANTINED' WHERE id IN (:localIds)")
suspend fun markQuarantined(localIds: List<Long>)
```

### 8.5 🟠 TripStatusPullWorker (Fix #7 — v3 corrected endpoint)

> **Problem:** Sync is push-only. Conductor has no way to discover their trip was cancelled server-side.

> ⚠️ **v3 fix:** Uses existing `GET /api/trips/?conductor={userId}` endpoint (already in backend), NOT `GET /api/trips/my-trips/` which does not exist. A new conductor-scoped convenience endpoint is added to §15 as optional.

```kotlin
// sync/TripStatusPullWorker.kt
@HiltWorker
class TripStatusPullWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tripApi: TripApi,
    private val tripDao: TripDao,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val activeTripIds = tripDao.getActiveTripIds()  // in_progress or scheduled
            if (activeTripIds.isEmpty()) return Result.success()

            // ⚡ v4 FIX: Filter by status to reduce bandwidth on metered mobile data.
            // Without filter, downloads ALL trips (including 60+ completed ones) every 30 min.
            // Uses existing endpoint: GET /api/trips/?status=scheduled,in_progress,cancelled
            val serverTrips = tripApi.getTrips(status = "scheduled,in_progress,cancelled")

            for (serverTrip in serverTrips.results) {
                val localTrip = tripDao.getById(serverTrip.id) ?: continue
                if (localTrip.status != serverTrip.status) {
                    // Status divergence detected!
                    tripDao.updateStatus(serverTrip.id, serverTrip.status)

                    if (serverTrip.status == "cancelled") {
                        notificationHelper.showTripCancelled(
                            "Trip ${serverTrip.tripCode} was cancelled by the office. " +
                            "Any unsynced tickets will be quarantined."
                        )
                    }
                }
            }
            Result.success()
        } catch (e: HttpException) {
            if (e.code() == 401) Result.failure()  // Token revoked, don't retry
            else Result.retry()  // Transient error, let WorkManager retry
        } catch (e: IOException) {
            Result.retry()  // Network error, let WorkManager retry with backoff
        }
    }
}

// Schedule: every 30 min when connected + on every manual sync
// Error handling: 401 → failure (stop), network error → retry with backoff
```

> **Backend note:** The existing `GET /api/trips/` endpoint already filters by conductor when the JWT belongs to a conductor role. If a dedicated `GET /api/trips/my-active/` endpoint is needed for performance (avoids paginating through old trips), it's tracked in §15 as an **optional optimization**.

### 8.6 🟡 Idempotency Key — Full SHA-256 (Fix #8)

> **Problem:** `.take(16)` truncation adds collision risk with zero benefit.

```kotlin
// Per-item idempotency key — one key per ticket/expense, NOT per batch.
// The backend stores one SyncLog entry per idempotency key.
fun generateIdempotencyKey(userId: Long, ticket: TicketEntity): String {
    val content = listOf(
        ticket.tripId,
        ticket.ticketType,          // "passenger", "cargo", "expense"
        ticket.passengerName ?: "ANON",
        ticket.origin,
        ticket.destination,
        ticket.price,
        ticket.createdAtEpochMs
    ).joinToString(":")
    val hash = MessageDigest.getInstance("SHA-256")
        .digest("$userId:$content".toByteArray())
        .joinToString("") { "%02x".format(it) }  // Full 64-char hex, NO truncation
    return "sync-$userId-$hash"
}

// Usage in SyncWorker — generate key per item, not per batch:
// val batchItems = pendingItems.map { item ->
//     SyncItem(
//         type = item.ticketType,
//         idempotencyKey = generateIdempotencyKey(userId, item),
//         payload = item.toPayload(),
//         localId = item.id   // Room entity PK — echoed back by backend
//     )
// }
```

> Backend `sync_log.key` column must be `VARCHAR(100)` — not 64, not 80. Key format: `sync-{userId}-{64 hex chars}`. With `BigAutoField` user IDs (up to 19 digits), max length = 5+19+1+64 = 89 chars. `VARCHAR(100)` provides safe margin. Add `CREATE UNIQUE INDEX idx_sync_log_key ON sync_log(key)` if not present.

---

## 9. Security Architecture (Updated)

| Concern | Solution | Audit Status |
|---------|----------|-------------|
| **Token storage** | `EncryptedSharedPreferences` (AES-256 + AndroidKeyStore) | ✅ Original |
| **Token refresh** | `Mutex` lock prevents WorkManager + UI race condition | ✅ Original |
| **Device binding** | ⚡ Self-generated `UUID` in EncryptedSharedPreferences | ✅ **Fixed** |
| **Stolen phone** | Admin revokes via `POST /api/admin/users/{id}/revoke-device/` → instant 401 | ✅ Original |
| **API keys** | No hardcoded secrets — backend URL in `BuildConfig` | ✅ Original |
| **SSL** | HTTPS-only + ⚡ **OkHttp CertificatePinner** (staging/release) | ✅ **Fixed** |
| **ProGuard/R8** | Full obfuscation in release builds | ✅ Original |
| **Logging** | Timber stripped in release (`isDebuggable` check) | ✅ Original |
| **Crash reporting** | ⚡ **Firebase Crashlytics** for field visibility | ✅ **Added** |

---

## 10. Offline-First Data Flow (Updated)

```
┌───────────────────────────────────────────────┐
│               Conductor Action                │
│  (Create ticket / Record expense)             │
└──────────────────┬────────────────────────────┘
                   │
                   ▼
┌───────────────────────────────────────────────┐
│            Room Database (SQLite)             │
│  • Saved immediately                         │
│  • syncStatus = PENDING                      │
│  • Ticket number auto-generated locally      │
└──────────────────┬────────────────────────────┘
                   │
         ┌─────────┴──────────┐
         │                    │
    Online?              Offline?
         │                    │
         ▼                    ▼
┌──────────────────┐  ┌─────────────────────────┐
│ SyncWorker fires │  │ Queued in Room           │
│ via:             │  │ OneTimeWork fires on     │
│ • OneTimeWork    │  │ NetworkType.CONNECTED    │
│   (on-connect)   │  │ (0 delay on reconnect)  │
│ • PeriodicWork   │  │                         │
│   (15 min)       │  │ MIUI: ForegroundService │
│ • ForegroundSvc  │  │ keeps alive during trip  │
│   (MIUI active)  │  └─────────────────────────┘
└────────┬─────────┘
         │
         ▼
┌───────────────────────────────────────────────┐
│        POST /api/sync/batch/                  │
│  • Full SHA-256 idempotency key (64 chars)   │
│  • Atomic: all-or-quarantine                 │
│  • Response: accepted[] / quarantined[]      │
└──────────────────┬────────────────────────────┘
                   │
         ┌─────────┴──────────────┐
         │                        │
    accepted[]              quarantined[]
         │                        │
         ▼                        ▼
┌──────────────────┐  ┌──────────────────────────┐
│ syncStatus=SYNCED│  │ syncStatus=QUARANTINED   │
│ synced_at = now()│  │ ⚠ Warning banner shown  │
│ Remove from queue│  │ Never retried            │
└──────────────────┘  │ Conductor contacts admin │
                      └──────────────────────────┘

    ┌──────────────────────────────────────────────────┐
    │ TripStatusPullWorker (every 30 min)               │
    │ GET /api/trips/?status=scheduled,in_progress,...  │
    │ Detects: cancelled, completed, etc.               │
    │ Updates Room + notifies conductor                 │
    └──────────────────────────────────────────────────┘
```

---

## 11. Verification Plan

### Automated Tests

| Test | Type | Command |
|------|------|---------|
| Unit tests (UseCases, Utils, IdempotencyKey) | JUnit 5 + Mockk | `./gradlew test` |
| ⚡ Room migration tests | `MigrationTestHelper` | `./gradlew connectedAndroidTest` |
| Room DAO tests (incl. QUARANTINED filter) | Instrumented | `./gradlew connectedAndroidTest` |
| Compose UI tests | Instrumented | `./gradlew connectedAndroidTest` |

### Manual Verification (on Redmi Turbo 4 Pro)

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 1 | **Login** | Open app → enter conductor phone + password → tap Login | Dashboard loads, sync badge shows "Synced" |
| 2 | **View trips** | Dashboard → see assigned trips | Only conductor's own trips appear |
| 3 | **Start trip** | Tap trip → "Start Trip" button | Status changes to "In Progress", dashboard updates |
| 4 | **Create passenger ticket** | FAB (+) → Passenger tab → fill form → Save | Ticket saved, dashboard revenue updates |
| 5 | **Create cargo ticket** | FAB (+) → Cargo tab → fill form → Save | Ticket saved with tier pricing |
| 6 | **Record expense** | Expenses tab → fill amount, pick category → Save | Expense saved, dashboard expense total updates |
| 7 | **Complete trip** | Trip detail → "Complete Trip" button | Status changes to "Completed", ticket creation blocked |
| 8 | **Offline workflow** | Airplane mode → create 5 tickets + 2 expenses | Saved locally, badge shows "7 pending" |
| 9 | **Auto-sync on reconnect** | Disable airplane mode → observe | ⚡ Sync fires immediately (OneTimeWork), not after 60s |
| 10 | **Idempotency** | Kill app during sync → reopen → wait for retry | No duplicate tickets on server |
| 11 | **Device revoke** | Admin revokes device from web portal | App shows login screen, "Device revoked" message |
| 12 | ⚡ **Quarantine visibility** | Cancel trip on web → conductor syncs | Warning banner: "X tickets quarantined — contact admin" |
| 13 | ⚡ **Trip status pull** | Cancel trip on web → wait 30 min | Conductor sees "Cancelled" status + notification |
| 14 | ⚡ **MIUI battery test** | Start trip → minimize app → wait 5 min | Foreground service notification visible, sync still works |
| 15 | **Build** | `./gradlew assembleRelease` | APK builds without errors, size < 10MB |

---

## 12. Dependencies & Risks (Updated)

### Prerequisites

| Dependency | Status | Owner |
|------------|--------|-------|
| Backend API running | ✅ Done | Backend team |
| `/api/sync/batch/` endpoint | ✅ Done | Backend team |
| `GET /api/trips/` filters by conductor JWT | ✅ Done | Backend team |
| ⚡ Shared Docker exports volume | 🔴 **TODO** | Backend team |
| ⚡ `UNIQUE INDEX` on `sync_log.key` (`VARCHAR(100)`) | 🔴 **TODO** | Backend team |
| ⚡ `GET /api/sync/log/{key}/` endpoint | 🔴 **TODO** | Backend team |
| ⚡ Server cert SPKI pins extracted | 🔴 **TODO** | Developer |
| Conductor test account | ✅ Seeded | Backend team |
| Android Studio + SDK | ✅ Installed | Developer |
| Redmi Turbo 4 Pro | ✅ Available | Developer |

### Risks & Mitigations (Updated)

| Risk | Impact | Mitigation |
|------|--------|------------|
| 🔴 **Keystore loss** | **Irreversible data wipe on all 8 devices** | Backup to cloud vault + USB drive immediately after generation. Verify restore works. |
| Room migration errors | **Data loss on update** | ⚡ Explicit `Migration` objects + `MigrationTestHelper` tests. **NEVER** `fallbackToDestructiveMigration()` |
| MIUI kills background sync | Sync delayed/stalled | ⚡ `SyncForegroundService` (`START_NOT_STICKY`) during active trips + battery whitelist guide in Profile |
| Cert pin rotation missed | **All 8 phones lose connectivity** | Calendar reminder Month 2, pin rotation SLA in §4, WhatsApp distribution channel |
| Compose recomposition jank | Janky ticket list | ⚡ `@Stable` models + `key = { id }` + profile on Redmi Turbo 4 Pro |
| Crashlytics silent on MIUI | No crash visibility on ~30% fleet | GMS availability check + local file fallback (§8.7) |
| Staging server unreachable | Can't test sync | `adb reverse tcp:8000 tcp:8000` for local dev first |

---

## 13. Timeline Summary (Updated — 21 days)

| Week | Phase | Tasks | Deliverable |
|------|-------|-------|-------------|
| **Week 1** (Day 1-4) | Scaffold + Auth | T30.1-T30.6, T31.1-T31.7 | App shell + working login + migration protocol |
| **Week 2** (Day 5-11) | Dashboard + Tickets | T32.1-T32.7, T33.1-T33.7 | Trip management + ticket creation + trip status pull |
| **Week 3** (Day 12-18) | Expenses + Sync | T34.1-T34.5, T35.1-T35.9 | Full offline-first workflow + quarantine handling + MIUI fix |
| **Week 4** (Day 19-21) | Testing + Polish | T3X.1-T3X.8 | Release APK + migration tests + all audit scenarios verified |

---

## 14. Done When (Updated)

- [ ] Conductor logs in with phone + password → dashboard loads
- [ ] Conductor sees ONLY their assigned trips
- [ ] Start trip → create tickets → record expenses → complete trip (full lifecycle)
- [ ] Full workflow works in airplane mode (offline-first)
- [ ] ⚡ Auto-sync on reconnect via `OneTimeWork` with `NetworkType.CONNECTED` — **not** periodic polling
- [ ] ⚡ Idempotent sync — full SHA-256 hash, no truncation
- [ ] ⚡ Partial sync: quarantined tickets marked `QUARANTINED` + warning banner shown
- [ ] ⚡ Trip status pull: cancelled trip detected → conductor notified
- [ ] ⚡ Device UUID (not Android ID) stored in EncryptedSharedPreferences
- [ ] ⚡ SSL certificate pinning on staging/release builds (with real pins, not placeholders)
- [ ] ⚡ Room migrations tested with `MigrationTestHelper` — no `fallbackToDestructiveMigration()`
- [ ] ⚡ MIUI foreground service keeps sync alive during active trips
- [ ] ⚡ Firebase Crashlytics reporting field crashes (with MIUI/GMS fallback)
- [ ] Device revocation → instant logout
- [ ] JWT stored in EncryptedSharedPreferences (not plaintext)
- [ ] 🔴 **Signing keystore** backed up to cloud vault + USB drive, restore verified
- [ ] 🔴 **APK update distribution** tested: send via WhatsApp → install over existing → data preserved
- [ ] 🔴 **Cert pin rotation SLA** documented with calendar reminder set
- [ ] APK size < 10MB
- [ ] All automated tests pass
- [ ] Full flow verified on Redmi Turbo 4 Pro

---

## 15. Backend Fixes (Not Mobile, But Blocking)

> These were identified in the audit and must be fixed in the backend before mobile goes to production:

| Fix | File | Effort | Status | Priority |
|-----|------|--------|--------|----------|
| Shared Docker volume for Celery exports | `docker-compose.yml` | 5 min | 🔴 TODO | P0 — Exports broken in Docker |
| `UNIQUE INDEX` on `sync_log.key` (`VARCHAR(100)`, not 64/80) | DB migration | 10 min | 🔴 TODO | P0 — O(n) scan on every sync |
| Replace `ignore_conflicts=True` in `bulk_create` | `api/views/sync.py` | 30 min | 🔴 TODO | P1 — Swallows all errors silently |
| `GET /api/sync/log/{key}/` — serializer + view + permission (conductor sees own logs only) + URL pattern + test | `api/views/sync.py`, `api/serializers/sync.py`, `api/urls.py` | **90 min** | 🔴 TODO | **P0 — Required for `already_processed` handler** |
| Verify `GET /api/trips/?status=...` query param is supported (DRF filter) | `api/views/trip_views.py` | 15 min | 🔴 TODO | **P0 — TripStatusPullWorker depends on this** |
| Extract server cert SPKI pins via `openssl` | Ops | 5 min | 🔴 TODO | **P0 — Cert pinning gate** |
| *(Optional)* `GET /api/trips/my-active/` — conductor-scoped active trips only | `api/views/trip_views.py` | 20 min | 🟡 NICE-TO-HAVE | Optimization for TripStatusPull |

---

## 16. Build Configuration Notes

### Room Schema Export (required for MigrationTestHelper)

> ⚠️ **Without this, `MigrationTestHelper` silently has no schema to validate against.** Migration tests will compile and pass without actually checking schema integrity.

```kotlin
// app/build.gradle.kts
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Also add schemas to test source sets:
android {
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}
```

> Commit the exported `schemas/` directory to version control — it provides the baseline for `MigrationTestHelper` to verify each migration preserves data.

### UUID Security Window on Reinstall

> **Known limitation:** On app reinstall, a new UUID is generated. The old device's refresh token remains valid for up to 7 days (JWT expiry). Mitigation: admin should revoke the old device binding when re-binding a reinstalled app. For 8 managed devices this is acceptable — the admin knows every conductor personally.

### 8.7 Crashlytics MIUI/GMS Fallback (v5)

> ⚠️ **Problem:** ~30% of budget Xiaomi phones have Google Play Services restricted or disabled. Crashlytics silently fails to initialize → zero crash visibility on those devices.

```kotlin
// SouigatApp.kt — conditional Crashlytics initialization
@HiltAndroidApp
class SouigatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (isGooglePlayServicesAvailable()) {
            FirebaseApp.initializeApp(this)
        } else {
            // Fallback: log crashes to local file for retrieval via adb
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                logCrashToLocalFile(throwable)
                defaultHandler?.uncaughtException(thread, throwable)  // Preserve default behavior
            }
        }
    }

    // ⚡ v6 FIX: Defined function (was undefined → compile error in v5)
    private fun logCrashToLocalFile(throwable: Throwable) {
        try {
            // Use getExternalFilesDir — filesDir is not adb-pullable on Android 10+ release builds
            val file = File(getExternalFilesDir(null), "crash_log.txt")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val entry = "[$timestamp] ${throwable::class.simpleName}: ${throwable.message}\n" +
                        "${throwable.stackTraceToString()}\n---\n"
            file.appendText(entry)
            // Retrieval: adb pull /sdcard/Android/data/com.souigat.mobile/files/crash_log.txt
        } catch (e: Exception) {
            // Silently ignore — we're already in a crash handler, can't do more
        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        return try {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false  // GMS not available at all
        }
    }

    companion object {
        private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    }
}
```

> **Crash retrieval:** For 8 managed devices, `adb pull` is sufficient — no sync endpoint needed. Run `adb pull /sdcard/Android/data/com.souigat.mobile/files/crash_log.txt` when investigating field issues.

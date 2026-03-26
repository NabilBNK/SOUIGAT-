# TASK.MD — COMPLETE REMEDIATION ROADMAP

All problems identified in raport1.md and raport2.md with prioritized solutions.

---

## TABLE OF CONTENTS

1. [Mobile (Android/Kotlin) Tasks](#mobile-androidkotlin-tasks)
2. [Web (React/TypeScript) Tasks](#web-reacttypescript-tasks)
3. [Backend (Python/Django) Tasks](#backend-pythondjango-tasks)
4. [Cross-System Integration Tasks](#cross-system-integration-tasks)
5. [Testing & Validation](#testing--validation)
6. [Documentation & Training](#documentation--training)

---

## MOBILE (ANDROID/KOTLIN) TASKS

### P0: CRITICAL — Data Correctness (Week 1)

#### Task M1: Fix Trip Status Fallback Logic (P0)

**Problem:**
- Trip completion can diverge: mobile marks trip "completed" locally + Firestore, but backend DB never updated
- Fallback-on-failure pattern treats 403 (permission denied) as retryable, falls back to Firestore-only
- No distinction between retryable (network) and terminal (permission) errors

**Files:**
- `mobile/app/src/main/java/com/souigat/mobile/data/repository/TripRepositoryImpl.kt`
- `mobile/app/src/main/java/com/souigat/mobile/worker/SyncDataWorker.kt`

**Solution:**

1. Add error classification enum in `TripRepositoryImpl.kt`:
   ```kotlin
   enum class ErrorCategory {
       NETWORK,        // Retryable: timeout, connection refused
       TRANSIENT,      // Retryable: 429 (rate limit), 5xx
       TERMINAL,       // Non-retryable: 403 (permission), 400 (bad request), 401 (auth)
       UNKNOWN
   }
   
   private fun classifyError(exception: Exception): ErrorCategory {
       return when (exception) {
           is SocketTimeoutException, is ConnectException -> ErrorCategory.NETWORK
           is HttpException -> when (exception.code()) {
               429, 500, 502, 503, 504 -> ErrorCategory.TRANSIENT
               400, 401, 403, 404 -> ErrorCategory.TERMINAL
               else -> ErrorCategory.UNKNOWN
           }
           else -> ErrorCategory.UNKNOWN
       }
   }
   ```

2. Update `completeTrip()` to never fallback on terminal errors:
   ```kotlin
   suspend fun completeTrip(tripId: Long, eventId: String): Result<Unit> {
       return try {
           // ALWAYS write backend first for trip status
           val response = tripApi.completeTrip(tripId, eventId)
           
           if (response.isSuccessful) {
               // Update local trip status
               tripDao.updateTripStatus(tripId, "completed")
               
               // Queue Firestore mirror update (async, non-critical)
               enqueueTripStatusFirebaseSync(tripId, "completed", eventId)
               
               Result.success(Unit)
           } else {
               val category = classifyError(response)
               
               // Terminal error: don't fallback, don't update local state
               if (category == ErrorCategory.TERMINAL) {
                   Result.failure(Exception("Permission denied: ${response.code()}"))
               } else {
                   // Transient/network error: throw for retry logic
                   Result.failure(Exception("Retryable error: ${response.code()}"))
               }
           }
       } catch (e: Exception) {
           val category = classifyError(e)
           if (category == ErrorCategory.TERMINAL) {
               Result.failure(e)  // Don't fallback
           } else {
               Result.failure(e)  // Caller will implement retry
           }
       }
   }
   ```

3. Remove fallback-to-Firestore-only logic in ViewModel:
   ```kotlin
   // In TripDetailViewModel.kt
   fun completeTrip() {
       viewModelScope.launch {
           try {
               val result = tripRepository.completeTrip(tripId, generateEventId())
               when {
                   result.isSuccess -> {
                       // Success: UI updates, Firestore sync happens async
                       _tripState.update { it.copy(status = "completed") }
                       _uiEvent.emit(UiEvent.ShowSuccess("Trip completed"))
                   }
                   result.isFailure -> {
                       // Failure: show error, DO NOT update UI
                       val error = result.exceptionOrNull()
                       _uiEvent.emit(UiEvent.ShowError(error?.message ?: "Unknown error"))
                   }
               }
           } catch (e: Exception) {
               _uiEvent.emit(UiEvent.ShowError("Error: ${e.message}"))
           }
       }
   }
   ```

**Acceptance Criteria:**
- ✅ `completeTrip()` always attempts backend first
- ✅ No Firestore-only fallback on terminal errors (403, 400, 401)
- ✅ 403 response does NOT update local trip status
- ✅ UI shows error message on terminal errors
- ✅ Manual test: Complete trip with auth revoked → see error, trip still "in_progress" locally

**Effort:** 3–4 hours

---

#### Task M2: Add Trip Status Backend Queue Type (P0)

**Problem:**
- Trip status sync is Firestore-only; unlike tickets/expenses which replay to backend first via queue
- If backend API fails, no retry queue for trip status
- No audit trail of trip status change attempts

**Files:**
- `mobile/app/src/main/java/com/souigat/mobile/data/local/entity/SyncQueueEntity.kt`
- `mobile/app/src/main/java/com/souigat/mobile/worker/SyncDataWorker.kt`
- `mobile/app/src/main/java/com/souigat/mobile/data/repository/TripRepositoryImpl.kt`

**Solution:**

1. Add new queue type to `SyncQueueEntity.kt`:
   ```kotlin
   @Entity(tableName = "sync_queue")
   data class SyncQueueEntity(
       @PrimaryKey(autoGenerate = true)
       val id: Long = 0,
       val entityType: String,  // "trip_status_backend" (NEW), "trip_status_firebase", "passenger_ticket", etc.
       val entityId: Long,
       val operation: String,   // "upsert", "delete"
       val payload: String,     // JSON
       val status: String,      // "pending", "syncing", "synced", "failed"
       val retryCount: Int = 0,
       val lastRetryAt: Long? = null,
       val createdAt: Long = System.currentTimeMillis(),
       val updatedAt: Long = System.currentTimeMillis(),
       val errorMessage: String? = null,
   )
   ```

2. Add enqueue function in `TripRepositoryImpl.kt`:
   ```kotlin
   private suspend fun enqueueTripStatusBackendSync(
       tripId: Long,
       status: String,
       eventId: String,
   ) {
       val payload = mapOf(
           "trip_id" to tripId,
           "status" to status,
           "event_id" to eventId,
           "synced_at" to System.currentTimeMillis(),
       )
       
       syncQueueDao.insert(
           SyncQueueEntity(
               entityType = "trip_status_backend",  // NEW
               entityId = tripId,
               operation = "upsert",
               payload = Json.encodeToString(payload),
               status = "pending",
           )
       )
   }
   ```

3. Update `completeTrip()` to enqueue after backend success:
   ```kotlin
   suspend fun completeTrip(tripId: Long, eventId: String): Result<Unit> {
       return try {
           val response = tripApi.completeTrip(tripId, eventId)
           
           if (response.isSuccessful) {
               tripDao.updateTripStatus(tripId, "completed")
               
               // NEW: Enqueue backend sync for audit/retry
               enqueueTripStatusBackendSync(tripId, "completed", eventId)
               
               // Also queue Firestore mirror (async)
               enqueueTripStatusFirebaseSync(tripId, "completed", eventId)
               
               // Trigger immediate sync
               SyncScheduler.requestSyncNow()
               
               Result.success(Unit)
           } else {
               // Handle error (as per Task M1)
           }
       } catch (e: Exception) {
           // Handle exception
       }
   }
   ```

4. Update `SyncDataWorker.kt` to handle `trip_status_backend`:
   ```kotlin
   private suspend fun syncItemToBackend(item: SyncQueueEntity): Boolean {
       if (item.entityType != "trip_status_backend") return false
       
       try {
           val payload = Json.decodeFromString<Map<String, Any>>(item.payload)
           val tripId = (payload["trip_id"] as? Number)?.toLong() ?: return false
           val status = payload["status"] as? String ?: return false
           
           val response = when (status) {
               "completed" -> tripApi.completeTrip(tripId, payload["event_id"] as String)
               "started" -> tripApi.startTrip(tripId)
               else -> return false
           }
           
           return response.isSuccessful
       } catch (e: Exception) {
           Timber.w("Failed to sync trip status to backend: ${e.message}")
           return false
       }
   }
   
   private suspend fun processQueueItem(item: SyncQueueEntity) {
       // NEW: Try backend first for trip_status_backend
       if (item.entityType == "trip_status_backend") {
           val success = syncItemToBackend(item)
           if (success) {
               syncQueueDao.updateItemStatus(item.id, "synced")
               return
           }
       }
       
       // Existing logic for other types (tickets, expenses, etc.)
       val mapped = mapEntityToFirebaseDocument(item)
       // ... Firestore sync logic
   }
   ```

**Acceptance Criteria:**
- ✅ New queue type `trip_status_backend` created
- ✅ `completeTrip()` enqueues `trip_status_backend` item
- ✅ Worker processes `trip_status_backend` → backend API first (not Firestore-only)
- ✅ Worker retries on transient errors (max 3 retries, exponential backoff)
- ✅ Worker marks as "failed" on terminal errors (403, 400)
- ✅ Manual test: Complete trip → verify queue item created with type `trip_status_backend` → verify worker calls backend API

**Effort:** 4–5 hours

---

#### Task M3: Route trip_status_backend to Backend API in Worker (P0)

**Problem:**
- Worker processes all queue items the same way (to Firestore only)
- Need explicit backend routing for trip status changes before Firestore mirror

**Files:**
- `mobile/app/src/main/java/com/souigat/mobile/worker/SyncDataWorker.kt`

**Solution:**

1. Update worker to route by entity type:
   ```kotlin
   override suspend fun doWork(): Result {
       val items = syncQueueDao.getItemsByStatus("pending", limit = 50)
       
       for (item in items) {
           syncQueueDao.updateItemStatus(item.id, "syncing")
           
           val success = when (item.entityType) {
               "trip_status_backend" -> syncTripStatusToBackend(item)
               "trip_status_firebase" -> syncTripStatusToFirebase(item)
               "passenger_ticket" -> syncTicketToFirestore(item)
               "cargo_ticket" -> syncTicketToFirestore(item)
               "trip_expense" -> syncExpenseToFirestore(item)
               else -> {
                   Timber.w("Unknown entity type: ${item.entityType}")
                   false
               }
           }
           
           if (success) {
               syncQueueDao.updateItemStatus(item.id, "synced")
           } else {
               handleSyncFailure(item)
           }
       }
       
       return Result.success()
   }
   
   private suspend fun syncTripStatusToBackend(item: SyncQueueEntity): Boolean {
       try {
           val payload = Json.decodeFromString<Map<String, Any>>(item.payload)
           val tripId = (payload["trip_id"] as? Number)?.toLong() ?: return false
           val status = payload["status"] as? String ?: return false
           val eventId = payload["event_id"] as? String ?: UUID.randomUUID().toString()
           
           val response = when (status) {
               "completed" -> tripApi.completeTrip(tripId, eventId)
               "started" -> tripApi.startTrip(tripId)
               else -> return false
           }
           
           if (!response.isSuccessful) {
               // Classify error and decide retry
               val shouldRetry = when (response.code()) {
                   429, 500, 502, 503, 504 -> true  // Transient
                   400, 401, 403, 404 -> false      // Terminal
                   else -> true                      // Unknown: retry
               }
               
               if (!shouldRetry) {
                   // Mark as terminal failure
                   syncQueueDao.updateItemStatus(item.id, "failed")
                   syncQueueDao.updateItemError(item.id, "Terminal error: ${response.code()}")
               }
               
               return false
           }
           
           Timber.d("Trip status synced to backend: trip=$tripId status=$status")
           return true
       } catch (e: Exception) {
           Timber.e(e, "Error syncing trip status to backend")
           return false
       }
   }
   
   private suspend fun handleSyncFailure(item: SyncQueueEntity) {
       val newRetryCount = item.retryCount + 1
       val maxRetries = 5
       
       if (newRetryCount >= maxRetries) {
           syncQueueDao.updateItemStatus(item.id, "failed")
           syncQueueDao.updateItemError(item.id, "Max retries exceeded")
           Timber.w("Sync item failed after $maxRetries retries: ${item.id}")
       } else {
           // Exponential backoff: 1s, 2s, 4s, 8s, 16s
           val delayMs = 1000L * (1 shl (newRetryCount - 1))
           
           syncQueueDao.update(
               item.copy(
                   status = "pending",
                   retryCount = newRetryCount,
                   lastRetryAt = System.currentTimeMillis(),
               )
           )
           
           // Schedule retry
           SyncScheduler.scheduleRetry(delayMs)
       }
   }
   ```

2. Update `SyncScheduler.kt` to request sync immediately for trip status:
   ```kotlin
   fun requestSyncNow(priority: Boolean = false) {
       val request = OneTimeWorkRequestBuilder<SyncDataWorker>()
           .apply {
               if (priority) {
                   setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED)
               }
           }
           .build()
       
       workManager.enqueueUniqueWork(
           "sync_data_worker",
           ExistingWorkPolicy.KEEP,  // Keep running worker, don't queue duplicate
           request,
       )
   }
   ```

3. Call `SyncScheduler.requestSyncNow(priority = true)` after enqueuing trip status:
   ```kotlin
   // In TripRepositoryImpl.completeTrip()
   enqueueTripStatusBackendSync(tripId, "completed", eventId)
   SyncScheduler.requestSyncNow(priority = true)  // Immediate, expedited
   ```

**Acceptance Criteria:**
- ✅ Worker routes `trip_status_backend` → `syncTripStatusToBackend()`
- ✅ Backend API called, response checked for 2xx
- ✅ Exponential backoff on transient errors (429, 5xx)
- ✅ Terminal errors (403, 400) marked "failed", not retried
- ✅ Manual test: Complete trip → watch worker process → verify backend called via API logs

**Effort:** 3–4 hours

---

#### Task M4: Add Timestamp-Aware Merge for Firestore Listeners (P0)

**Problem:**
- Firestore realtime listeners directly overwrite local Room data without timestamp comparison
- Stale Firestore update can wipe unsync'd conductor-created tickets
- No conflict resolution strategy

**Files:**
- `mobile/app/src/main/java/com/souigat/mobile/data/firebase/FirebaseTripDataSource.kt`

**Solution:**

1. Add timestamp merge logic to `FirebaseTripDataSource.kt`:
   ```kotlin
   class FirebaseTripDataSource(
       private val firestore: FirebaseFirestore,
       private val ticketDao: PassengerTicketDao,
       private val cargoTicketDao: CargoTicketDao,
   ) {
       fun listenToPassengerTickets(tripId: Long): Flow<List<PassengerTicket>> = callbackFlow {
           val listener = firestore
               .collection("passenger_ticket_mirror_v1")
               .whereEqualTo("trip_id", tripId)
               .whereEqualTo("is_deleted", false)
               .addSnapshotListener { snapshot, error ->
                   if (error != null) {
                       Timber.w(error, "Passenger ticket listener failed")
                       return@addSnapshotListener
                   }
                   
                   if (snapshot != null) {
                       // Merge Firestore updates with local Room data
                       mergeTicketsFromFirestore(
                           tripId,
                           snapshot.documents,
                           ticketDao,
                       )
                   }
               }
           
           awaitClose { listener.remove() }
       }
       
       private suspend fun mergeTicketsFromFirestore(
           tripId: Long,
           documents: List<DocumentSnapshot>,
           dao: PassengerTicketDao,
       ) {
           val firestoreTickets = documents.mapNotNull { doc ->
               try {
                   doc.toObject(PassengerTicketFirestoreDto::class.java)
               } catch (e: Exception) {
                   Timber.w(e, "Failed to parse ticket from Firestore")
                   null
               }
           }
           
           for (fsTicket in firestoreTickets) {
               val localTicket = dao.getTicketById(fsTicket.id)
               
               if (localTicket == null) {
                   // New ticket from Firestore: insert directly
                   dao.insert(fsTicket.toEntity())
                   Timber.d("New ticket from Firestore: ${fsTicket.id}")
               } else {
                   // Existing ticket: merge with timestamp comparison
                   val localTimestamp = parseTimestamp(localTicket.updatedAt)
                   val firestoreTimestamp = parseTimestamp(fsTicket.sourceUpdatedAt)
                   
                   if (firestoreTimestamp > localTimestamp) {
                       // Firestore is newer: update local
                       dao.update(fsTicket.toEntity())
                       Timber.d("Updated ticket from Firestore (newer): ${fsTicket.id}")
                   } else if (firestoreTimestamp < localTimestamp) {
                       // Local is newer: don't overwrite (queue will sync local to Firestore)
                       Timber.d("Ignoring stale Firestore update for ticket ${fsTicket.id} (local is ${localTimestamp - firestoreTimestamp}ms newer)")
                   } else {
                       // Same timestamp: compare payload hash (optional, skip for now)
                       Timber.d("Same timestamp for ticket ${fsTicket.id}, skipping merge")
                   }
               }
           }
       }
       
       private fun parseTimestamp(value: String?): Long {
           if (value.isNullOrEmpty()) return 0L
           return try {
               SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(value)?.time ?: 0L
           } catch (e: Exception) {
               Timber.w(e, "Failed to parse timestamp: $value")
               0L
           }
       }
   }
   ```

2. Add Firestore DTO with timestamp:
   ```kotlin
   data class PassengerTicketFirestoreDto(
       @DocumentId
       val id: Long = 0,
       val trip_id: Long = 0,
       val ticket_number: String = "",
       val passenger_name: String = "",
       val price: Long = 0,
       val status: String = "active",
       val source_created_at: String = "",
       val source_updated_at: String = "",  // Timestamp for merge
       val is_deleted: Boolean = false,
   ) {
       fun toEntity(): PassengerTicketEntity {
           return PassengerTicketEntity(
               id = id,
               tripId = trip_id,
               ticketNumber = ticket_number,
               passengerName = passenger_name,
               price = price,
               status = status,
               createdAt = source_created_at,
               updatedAt = source_updated_at,
           )
       }
   }
   ```

3. Log merge decisions for debugging:
   ```kotlin
   // In mergeTicketsFromFirestore()
   Timber.d("""
       Merge decision for ticket ${fsTicket.id}:
       Local: ${localTicket?.status} (${localTimestamp}ms)
       Firestore: ${fsTicket.status} (${firestoreTimestamp}ms)
       Decision: ${if (firestoreTimestamp > localTimestamp) "UPDATE" else "SKIP"}
   """.trimIndent())
   ```

**Acceptance Criteria:**
- ✅ Firestore listener parses timestamp from documents
- ✅ Local Room data compared by timestamp before merge
- ✅ Newer Firestore data overwrites local
- ✅ Stale Firestore data is ignored (not overwritten)
- ✅ Merge decisions logged with timestamps
- ✅ Manual test: Create ticket locally → verify local timestamp → mock stale Firestore update → verify local NOT overwritten

**Effort:** 3–4 hours

---

#### Task M5: Enforce Retryable Error Classification (P0)

**Problem:**
- No distinction between retryable (network timeout) and terminal (permission denied) errors
- Fallback pattern treats all errors the same (retry indefinitely or fallback to Firestore)
- App can get stuck retrying impossible operations

**Files:**
- `mobile/app/src/main/java/com/souigat/mobile/data/repository/TripRepositoryImpl.kt`
- `mobile/app/src/main/java/com/souigat/mobile/worker/SyncDataWorker.kt`
- `mobile/app/src/main/java/com/souigat/mobile/util/ErrorClassifier.kt` (new)

**Solution:**

1. Create `ErrorClassifier.kt`:
   ```kotlin
   object ErrorClassifier {
       enum class Category {
           NETWORK,      // Retryable: connection issues
           TRANSIENT,    // Retryable: server busy, rate limited
           TERMINAL,     // Non-retryable: auth, permission, bad request
           UNKNOWN,      // Unknown: treat as retryable
       }
       
       fun classify(exception: Exception): Category {
           return when (exception) {
               is SocketTimeoutException,
               is ConnectException,
               is SocketException -> Category.NETWORK
               
               is HttpException -> classifyHttpError(exception.code())
               
               else -> {
                   Timber.w("Unknown exception type: ${exception.javaClass.simpleName}")
                   Category.UNKNOWN
               }
           }
       }
       
       fun classify(httpCode: Int): Category {
           return classifyHttpError(httpCode)
       }
       
       private fun classifyHttpError(code: Int): Category {
           return when (code) {
               // Transient: server errors
               429 -> Category.TRANSIENT  // Too many requests
               500, 502, 503, 504 -> Category.TRANSIENT  // Server errors
               
               // Terminal: client errors
               400 -> Category.TERMINAL  // Bad request (invalid input)
               401 -> Category.TERMINAL  // Unauthorized (token expired, not refreshable)
               403 -> Category.TERMINAL  // Forbidden (permission denied)
               404 -> Category.TERMINAL  // Not found (resource deleted)
               
               // Others: treat as transient
               else -> Category.UNKNOWN
           }
       }
       
       fun isRetryable(category: Category): Boolean {
           return category == Category.NETWORK || category == Category.TRANSIENT || category == Category.UNKNOWN
       }
   }
   ```

2. Use classifier in `TripRepositoryImpl.kt`:
   ```kotlin
   suspend fun completeTrip(tripId: Long, eventId: String): Result<Unit> {
       return try {
           val response = tripApi.completeTrip(tripId, eventId)
           
           if (response.isSuccessful) {
               tripDao.updateTripStatus(tripId, "completed")
               enqueueTripStatusBackendSync(tripId, "completed", eventId)
               Result.success(Unit)
           } else {
               val category = ErrorClassifier.classify(response.code())
               
               if (ErrorClassifier.isRetryable(category)) {
                   // Transient error: throw for caller to retry
                   Result.failure(Exception("Retryable error: ${response.code()} (${response.message()})"))
               } else {
                   // Terminal error: don't retry, don't update local state
                   Result.failure(Exception("Terminal error: ${response.code()} (${response.message()})"))
               }
           }
       } catch (e: Exception) {
           val category = ErrorClassifier.classify(e)
           
           if (ErrorClassifier.isRetryable(category)) {
               Result.failure(Exception("Retryable error: ${e.message}"))
           } else {
               Result.failure(Exception("Terminal error: ${e.message}"))
           }
       }
   }
   ```

3. Use classifier in worker retry logic:
   ```kotlin
   private suspend fun handleSyncFailure(item: SyncQueueEntity) {
       // Try to classify error from stored error message
       val category = try {
           val httpCode = item.errorMessage?.substringAfterLast(":")?.trim()?.toIntOrNull()
           if (httpCode != null) ErrorClassifier.classify(httpCode) else ErrorClassifier.Category.UNKNOWN
       } catch (e: Exception) {
           ErrorClassifier.Category.UNKNOWN
       }
       
       if (!ErrorClassifier.isRetryable(category)) {
           // Terminal error: don't retry
           syncQueueDao.updateItemStatus(item.id, "failed")
           syncQueueDao.updateItemError(item.id, "Terminal error (${category.name}): ${item.errorMessage}")
           Timber.w("Terminal error for sync item ${item.id}, stopping retries")
           return
       }
       
       // Retryable error: retry with backoff
       val newRetryCount = item.retryCount + 1
       val maxRetries = 5
       
       if (newRetryCount >= maxRetries) {
           syncQueueDao.updateItemStatus(item.id, "failed")
           syncQueueDao.updateItemError(item.id, "Max retries exceeded")
           Timber.w("Sync item failed after $maxRetries retries: ${item.id}")
       } else {
           val delayMs = 1000L * (1 shl (newRetryCount - 1))
           syncQueueDao.update(
               item.copy(
                   status = "pending",
                   retryCount = newRetryCount,
                   lastRetryAt = System.currentTimeMillis(),
               )
           )
           SyncScheduler.scheduleRetry(delayMs)
       }
   }
   ```

**Acceptance Criteria:**
- ✅ `ErrorClassifier` correctly classifies HTTP codes (400, 401, 403, 429, 5xx, etc.)
- ✅ Terminal errors (403, 400) NOT retried
- ✅ Network errors (timeout) ARE retried
- ✅ Worker uses classifier to decide retry vs fail
- ✅ Log shows "Terminal error" vs "Retryable error" classification
- ✅ Manual test: Trigger 403 → verify queue item marked "failed", NOT retried → verify "Terminal error" logged

**Effort:** 2–3 hours

---

#### Task M6: Trigger Immediate Sync for Trip Status (P0)

**Problem:**
- Trip status changes currently rely on 15-minute periodic sync schedule
- Users see stale "in_progress" for minutes after completing trip
- No urgent sync trigger for critical operations

**Files:**
- `mobile/app/src/main/java/com/souigat/mobile/data/repository/TripRepositoryImpl.kt`
- `mobile/app/src/main/java/com/souigat/mobile/worker/SyncScheduler.kt`

**Solution:**

1. Add priority sync request to `SyncScheduler.kt`:
   ```kotlin
   object SyncScheduler {
       fun requestSyncNow(priority: Boolean = false) {
           val request = OneTimeWorkRequestBuilder<SyncDataWorker>()
               .apply {
                   if (priority) {
                       setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED)
                   }
               }
               .build()
           
           workManager.enqueueUniqueWork(
               "sync_data_worker_immediate",
               ExistingWorkPolicy.KEEP,
               request,
           )
       }
       
       fun schedulePeriodicSync() {
           val periodicRequest = PeriodicWorkRequestBuilder<SyncDataWorker>(
               15,  // minutes
               TimeUnit.MINUTES,
           ).build()
           
           workManager.enqueueUniquePeriodicWork(
               "sync_data_worker_periodic",
               ExistingPeriodicWorkPolicy.KEEP,
               periodicRequest,
           )
       }
   }
   ```

2. Call `requestSyncNow(priority = true)` after trip status operations:
   ```kotlin
   // In TripRepositoryImpl.kt
   
   suspend fun completeTrip(tripId: Long, eventId: String): Result<Unit> {
       try {
           val response = tripApi.completeTrip(tripId, eventId)
           if (response.isSuccessful) {
               tripDao.updateTripStatus(tripId, "completed")
               enqueueTripStatusBackendSync(tripId, "completed", eventId)
               
               // IMMEDIATE sync (priority)
               SyncScheduler.requestSyncNow(priority = true)
               
               Result.success(Unit)
           } else {
               // Handle error
           }
       } catch (e: Exception) {
           // Handle exception
       }
   }
   
   suspend fun startTrip(tripId: Long): Result<Unit> {
       try {
           val response = tripApi.startTrip(tripId)
           if (response.isSuccessful) {
               tripDao.updateTripStatus(tripId, "in_progress")
               enqueueTripStatusBackendSync(tripId, "in_progress", UUID.randomUUID().toString())
               
               // IMMEDIATE sync
               SyncScheduler.requestSyncNow(priority = true)
               
               Result.success(Unit)
           } else {
               // Handle error
           }
       } catch (e: Exception) {
           // Handle exception
       }
   }
   ```

3. Add visual feedback in UI (TripDetailViewModel):
   ```kotlin
   class TripDetailViewModel(...) : ViewModel() {
       private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.IDLE)
       val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
       
       fun completeTrip() {
           viewModelScope.launch {
               _syncStatus.emit(SyncStatus.SYNCING)
               
               try {
                   val result = tripRepository.completeTrip(tripId, generateEventId())
                   
                   if (result.isSuccess) {
                       _tripState.update { it.copy(status = "completed") }
                       
                       // Wait 1-2 seconds for sync to start, then mark as complete
                       delay(2000)
                       _syncStatus.emit(SyncStatus.SUCCESS)
                       
                       delay(1000)
                       _syncStatus.emit(SyncStatus.IDLE)
                   } else {
                       _syncStatus.emit(SyncStatus.ERROR)
                       _uiEvent.emit(UiEvent.ShowError(result.exceptionOrNull()?.message ?: "Unknown error"))
                   }
               } catch (e: Exception) {
                   _syncStatus.emit(SyncStatus.ERROR)
                   _uiEvent.emit(UiEvent.ShowError(e.message ?: "Error"))
               }
           }
       }
   }
   
   enum class SyncStatus {
       IDLE, SYNCING, SUCCESS, ERROR
   }
   ```

**Acceptance Criteria:**
- ✅ `requestSyncNow(priority = true)` called after `completeTrip()` and `startTrip()`
- ✅ Worker processes queued item within 1–2 seconds (not 15 minutes)
- ✅ UI shows "Syncing..." badge while worker runs
- ✅ Manual test: Complete trip → see "Syncing..." badge → verify disappears in 2–3 seconds

**Effort:** 2–3 hours

---

#### Task M7: Log Stuck Syncing Items on Reset (P0)

**Problem:**
- Items stuck in SYNCING status are reset to PENDING on app restart without audit trail
- No visibility into how often this happens or why
- Difficult to debug stuck sync issues

**Files:**
- `mobile/app/src/main/java/com/souigat/mobile/worker/SyncDataWorker.kt`
- `mobile/app/src/main/java/com/souigat/mobile/data/local/dao/SyncQueueDao.kt`

**Solution:**

1. Add stuck item detection and logging to `SyncDataWorker.kt`:
   ```kotlin
   override suspend fun doWork(): Result {
       // On worker start, check for stuck items from previous run
       resetStuckSyncingItems()
       
       // Process pending items
       val items = syncQueueDao.getItemsByStatus("pending", limit = 50)
       for (item in items) {
           syncQueueDao.updateItemStatus(item.id, "syncing")
           val success = syncItem(item)
           if (success) {
               syncQueueDao.updateItemStatus(item.id, "synced")
           } else {
               handleSyncFailure(item)
           }
       }
       
       return Result.success()
   }
   
   private suspend fun resetStuckSyncingItems() {
       val syncingItems = syncQueueDao.getItemsByStatus("syncing", limit = 1000)
       
       if (syncingItems.isNotEmpty()) {
           Timber.w("Found ${syncingItems.size} stuck items in SYNCING status, resetting to PENDING")
           
           for (item in syncingItems) {
               Timber.w("""
                   Resetting stuck sync item:
                   - ID: ${item.id}
                   - Type: ${item.entityType}
                   - Entity: ${item.entityId}
                   - Created: ${formatTimestamp(item.createdAt)}
                   - Last updated: ${formatTimestamp(item.updatedAt)}
                   - Retries: ${item.retryCount}
               """.trimIndent())
               
               // Log to database for audit
               val auditEntry = SyncAuditEntry(
                   syncQueueItemId = item.id,
                   action = "STUCK_RESET",
                   details = """
                       Stuck item reset on app restart
                       Entity: ${item.entityType}/${item.entityId}
                       Age: ${System.currentTimeMillis() - item.updatedAt}ms
                       Retries: ${item.retryCount}
                   """.trimIndent(),
                   timestamp = System.currentTimeMillis(),
               )
               syncAuditDao.insert(auditEntry)
               
               // Reset to pending for retry
               syncQueueDao.update(
                   item.copy(
                       status = "pending",
                       retryCount = item.retryCount + 1,
                       lastRetryAt = System.currentTimeMillis(),
                   )
               )
           }
       }
   }
   
   private fun formatTimestamp(ms: Long): String {
       return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))
   }
   ```

2. Create audit table to track stuck item resets:
   ```kotlin
   @Entity(tableName = "sync_audit")
   data class SyncAuditEntry(
       @PrimaryKey(autoGenerate = true)
       val id: Long = 0,
       val syncQueueItemId: Long,
       val action: String,  // "STUCK_RESET", "FAILED", "SUCCESS", etc.
       val details: String,
       val timestamp: Long = System.currentTimeMillis(),
   )
   
   @Dao
   interface SyncAuditDao {
       @Insert
       suspend fun insert(entry: SyncAuditEntry)
       
       @Query("SELECT * FROM sync_audit ORDER BY timestamp DESC LIMIT :limit")
       suspend fun getRecentEntries(limit: Int = 100): List<SyncAuditEntry>
       
       @Query("SELECT COUNT(*) FROM sync_audit WHERE action = 'STUCK_RESET'")
       suspend fun countStuckResets(): Int
   }
   ```

3. Add audit to database migrations:
   ```kotlin
   object Migrations {
       val MIGRATION_5_6 = object : Migration(5, 6) {
           override fun migrate(database: SupportSQLiteDatabase) {
               database.execSQL("""
                   CREATE TABLE IF NOT EXISTS sync_audit (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       syncQueueItemId INTEGER NOT NULL,
                       action TEXT NOT NULL,
                       details TEXT,
                       timestamp INTEGER NOT NULL
                   )
               """)
               database.execSQL("CREATE INDEX idx_sync_audit_timestamp ON sync_audit(timestamp)")
           }
       }
   }
   ```

4. Add debug UI to show stuck resets (optional dashboard):
   ```kotlin
   // In a diagnostic/debug screen
   viewModel.syncAuditEntries.collect { entries ->
       val stuckResets = entries.filter { it.action == "STUCK_RESET" }
       Text("Stuck resets: ${stuckResets.size}")
       LazyColumn {
           items(stuckResets) { entry ->
               Text("${entry.details} — ${formatTimestamp(entry.timestamp)}")
           }
       }
   }
   ```

**Acceptance Criteria:**
- ✅ On worker start, check for SYNCING items
- ✅ Log each stuck item with details (ID, type, age, retry count)
- ✅ Create `SyncAuditEntry` for each reset
- ✅ Audit table persists across app restarts
- ✅ Manual test: Start sync → kill app → restart → verify audit entry created with "STUCK_RESET"

**Effort:** 2–3 hours

---

### P1: Data Integrity (Week 2)

#### Task M8: Implement Idempotency for Trip Status (P1)

**Problem:**
- Multiple rapid trip completions create multiple queue entries (not deduplicated)
- Can lead to duplicate "completed" events or conflicting state
- No idempotency key for trip status operations

**Files:**
- `mobile/app/src/main/java/com/souigat/mobile/data/repository/TripRepositoryImpl.kt`
- `mobile/app/src/main/java/com/souigat/mobile/data/local/entity/SyncQueueEntity.kt`

**Solution:**

1. Add deduplication key to `SyncQueueEntity`:
   ```kotlin
   @Entity(
       tableName = "sync_queue",
       indices = [
           Index("dedupeKey", unique = true),  // NEW: enforce uniqueness
           Index("status"),
       ]
   )
   data class SyncQueueEntity(
       @PrimaryKey(autoGenerate = true)
       val id: Long = 0,
       val entityType: String,
       val entityId: Long,
       val operation: String,
       val payload: String,
       val status: String,
       val retryCount: Int = 0,
       val lastRetryAt: Long? = null,
       val createdAt: Long = System.currentTimeMillis(),
       val updatedAt: Long = System.currentTimeMillis(),
       val errorMessage: String? = null,
       val dedupeKey: String,  // NEW: "trip_status_backend:123:completed"
   )
   ```

2. Update `enqueueTripStatusBackendSync()` to use dedupeKey:
   ```kotlin
   private suspend fun enqueueTripStatusBackendSync(
       tripId: Long,
       status: String,
       eventId: String,
   ) {
       val payload = mapOf(
           "trip_id" to tripId,
           "status" to status,
           "event_id" to eventId,
           "synced_at" to System.currentTimeMillis(),
       )
       
       // Deduplication key: same trip/status = same key
       val dedupeKey = "trip_status_backend:$tripId:$status"
       
       try {
           syncQueueDao.insert(
               SyncQueueEntity(
                   entityType = "trip_status_backend",
                   entityId = tripId,
                   operation = "upsert",
                   payload = Json.encodeToString(payload),
                   status = "pending",
                   dedupeKey = dedupeKey,
               )
           )
       } catch (e: SQLiteConstraintException) {
           // Duplicate key: item already queued for this trip/status
           Timber.w("Trip status already queued: $dedupeKey")
           
           // Optional: update existing item with new eventId if needed
           val existing = syncQueueDao.getItemByDedupeKey(dedupeKey)
           if (existing != null && existing.status == "pending") {
               // Refresh the item (reset retry count, update payload with new eventId)
               syncQueueDao.update(
                   existing.copy(
                       payload = Json.encodeToString(payload),
                       retryCount = 0,
                       lastRetryAt = null,
                       updatedAt = System.currentTimeMillis(),
                   )
               )
               Timber.d("Updated pending trip status item: $dedupeKey")
           }
       }
   }
   ```

3. Add DAO method to query by dedupeKey:
   ```kotlin
   @Dao
   interface SyncQueueDao {
       // ... existing methods ...
       
       @Query("SELECT * FROM sync_queue WHERE dedupeKey = :dedupeKey LIMIT 1")
       suspend fun getItemByDedupeKey(dedupeKey: String): SyncQueueEntity?
   }
   ```

4. Update database migration:
   ```kotlin
   object Migrations {
       val MIGRATION_6_7 = object : Migration(6, 7) {
           override fun migrate(database: SupportSQLiteDatabase) {
               database.execSQL("ALTER TABLE sync_queue ADD COLUMN dedupeKey TEXT NOT NULL DEFAULT ''")
               database.execSQL("CREATE UNIQUE INDEX idx_sync_queue_dedupe ON sync_queue(dedupeKey)")
           }
       }
   }
   ```

**Acceptance Criteria:**
- ✅ Deduplication key created for trip status: "trip_status_backend:tripId:status"
- ✅ Multiple calls to `completeTrip()` with same tripId only create 1 queue item
- ✅ Second call to `completeTrip()` updates existing item (refresh eventId, reset retries)
- ✅ Manual test: Call `completeTrip(123)` twice → verify only 1 queue item created

**Effort:** 2–3 hours

---

#### Task M9: Add Timestamp to Trip Mirror Document (P1)

**Problem:**
- Trip mirror on Firestore lacks timestamp for conflict detection
- When mobile pushes trip status, Firestore needs to track when update occurred
- Helps with web merge logic

**Files:**
- `mobile/app/src/main/java/com/souigat/mobile/worker/SyncDataWorker.kt`
- `backend/api/services/firebase_mirror.py` (for comparison)

**Solution:**

1. Add timestamp to trip Firestore document in `SyncDataWorker.kt`:
   ```kotlin
   private suspend fun syncTripStatusToFirebase(item: SyncQueueEntity): Boolean {
       try {
           val payload = Json.decodeFromString<Map<String, Any>>(item.payload)
           val tripId = (payload["trip_id"] as? Number)?.toLong() ?: return false
           val status = payload["status"] as? String ?: return false
           
           val firestoreData = mapOf(
               "id" to tripId,
               "status" to status,
               "source_updated_at" to Timestamp.now(),  // NEW: for conflict detection
               "last_sync_at" to System.currentTimeMillis(),
               "last_op_id" to (payload["event_id"] as? String ?: ""),
           )
           
           val docRef = firestore
               .collection("trip_mirror_v1")
               .document(tripId.toString())
           
           docRef.set(firestoreData, SetOptions.merge()).await()
           
           Timber.d("Trip status synced to Firestore: trip=$tripId status=$status ts=${Timestamp.now()}")
           return true
       } catch (e: Exception) {
           Timber.e(e, "Error syncing trip status to Firestore")
           return false
       }
   }
   ```

2. Verify web receives and uses timestamp (see raport2.md Task W4):
   ```typescript
   // web/src/hooks/useTripMirrorData.ts
   interface TripStatusMirrorState {
       status: TripStatus | null
       sourceUpdatedAt: string | null  // timestamp from Firestore
   }
   ```

**Acceptance Criteria:**
- ✅ Trip mirror document includes `source_updated_at` timestamp
- ✅ Timestamp set to current time on sync
- ✅ Web can read and compare timestamps
- ✅ Manual test: Sync trip status → verify Firestore document has `source_updated_at`

**Effort:** 1–2 hours

---

### P2: UX & Diagnostics (Week 3)

#### Task M10: Add Sync Status UI Indicator (P2)

**Problem:**
- Users don't see visual feedback during sync operations
- "Syncing..." indicator helps users know their action is being processed
- No pending count visibility

**Files:**
- `mobile/app/src/main/java/com/souigat/mobile/ui/screens/trips/TripDetailViewModel.kt`
- `mobile/app/src/main/java/com/souigat/mobile/ui/screens/trips/TripDetailScreen.kt`

**Solution:**

1. Add sync status to ViewModel:
   ```kotlin
   class TripDetailViewModel(...) : ViewModel() {
       private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.IDLE)
       val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
       
       private val _pendingSyncCount = MutableStateFlow(0)
       val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()
       
       init {
           // Monitor sync queue for pending items
           viewModelScope.launch {
               syncQueueDao.observeItemsByStatus("pending").collect { items ->
                   _pendingSyncCount.emit(items.size)
               }
           }
           
           // Monitor for active syncs
           viewModelScope.launch {
               syncQueueDao.observeItemsByStatus("syncing").collect { items ->
                   _syncStatus.emit(
                       if (items.isNotEmpty()) SyncStatus.SYNCING else SyncStatus.IDLE
                   )
               }
           }
       }
       
       fun completeTrip() {
           viewModelScope.launch {
               _syncStatus.emit(SyncStatus.SYNCING)
               try {
                   val result = tripRepository.completeTrip(tripId, generateEventId())
                   if (result.isSuccess) {
                       _tripState.update { it.copy(status = "completed") }
                       delay(2000)  // Wait for queue to pick up item
                       _syncStatus.emit(SyncStatus.SUCCESS)
                       delay(1000)
                       _syncStatus.emit(SyncStatus.IDLE)
                   } else {
                       _syncStatus.emit(SyncStatus.ERROR)
                   }
               } catch (e: Exception) {
                   _syncStatus.emit(SyncStatus.ERROR)
               }
           }
       }
   }
   
   enum class SyncStatus {
       IDLE, SYNCING, SUCCESS, ERROR
   }
   ```

2. Add DAO methods to observe sync status:
   ```kotlin
   @Dao
   interface SyncQueueDao {
       @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY createdAt DESC")
       fun observeItemsByStatus(status: String): Flow<List<SyncQueueEntity>>
       
       @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'")
       fun observePendingCount(): Flow<Int>
   }
   ```

3. Update UI to show sync badge:
   ```kotlin
   // In TripDetailScreen.kt
   @Composable
   fun TripDetailScreen(viewModel: TripDetailViewModel) {
       val syncStatus by viewModel.syncStatus.collectAsState()
       val pendingCount by viewModel.pendingSyncCount.collectAsState()
       
       Column {
           // Header with status badge
           Row(
               modifier = Modifier
                   .fillMaxWidth()
                   .padding(16.dp),
               horizontalArrangement = Arrangement.SpaceBetween,
               verticalAlignment = Alignment.CenterVertically,
           ) {
               Text("Trip #$tripId")
               
               // Sync status badge
               when (syncStatus) {
                   SyncStatus.IDLE -> {
                       if (pendingCount > 0) {
                           Badge(
                               backgroundColor = Color(0xFFFFC107),  // Amber
                               contentColor = Color.Black,
                           ) {
                               Text("$pendingCount pending", fontSize = 12.sp)
                           }
                       }
                   }
                   SyncStatus.SYNCING -> {
                       Badge(backgroundColor = Color(0xFF2196F3)) {  // Blue
                           Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                               CircularProgressIndicator(
                                   modifier = Modifier.size(12.dp),
                                   strokeWidth = 1.dp,
                                   color = Color.White,
                               )
                               Spacer(Modifier.width(4.dp))
                               Text("Syncing...", fontSize = 12.sp, color = Color.White)
                           }
                       }
                   }
                   SyncStatus.SUCCESS -> {
                       Badge(backgroundColor = Color(0xFF4CAF50)) {  // Green
                           Text("✓ Synced", fontSize = 12.sp, color = Color.White)
                       }
                   }
                   SyncStatus.ERROR -> {
                       Badge(backgroundColor = Color(0xFFF44336)) {  // Red
                           Text("✗ Error", fontSize = 12.sp, color = Color.White)
                       }
                   }
               }
           }
           
           // Rest of UI...
       }
   }
   ```

**Acceptance Criteria:**
- ✅ Sync status badge shows "Syncing..." during worker processing
- ✅ Badge shows "✓ Synced" after sync completes
- ✅ Badge shows pending count when idle
- ✅ Manual test: Complete trip → see "Syncing..." badge → verify updates to "✓ Synced"

**Effort:** 2–3 hours

---

#### Task M11: Add Queue Retry Details UI (P2)

**Problem:**
- Users can't see why sync failed or if it's retrying
- No visibility into backoff strategy or retry count
- Difficult to diagnose stuck items

**Files:**
- `mobile/app/src/main/java/com/souigat/mobile/ui/screens/debug/SyncQueueDebugScreen.kt` (new)

**Solution:**

1. Create debug screen to show queue:
   ```kotlin
   @Composable
   fun SyncQueueDebugScreen(viewModel: SyncQueueDebugViewModel) {
       val queueItems by viewModel.queueItems.collectAsState()
       val auditEntries by viewModel.auditEntries.collectAsState()
       
       LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
           item {
               Text("Sync Queue (${queueItems.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
               Divider()
           }
           
           items(queueItems) { item ->
               SyncQueueItemRow(item)
           }
           
           item {
               Spacer(Modifier.height(16.dp))
               Text("Audit Log (${auditEntries.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
               Divider()
           }
           
           items(auditEntries) { entry ->
               SyncAuditEntryRow(entry)
           }
       }
   }
   
   @Composable
   fun SyncQueueItemRow(item: SyncQueueEntity) {
       val statusColor = when (item.status) {
           "pending" -> Color(0xFFFFC107)
           "syncing" -> Color(0xFF2196F3)
           "synced" -> Color(0xFF4CAF50)
           "failed" -> Color(0xFFF44336)
           else -> Color.Gray
       }
       
       Column(
           modifier = Modifier
               .fillMaxWidth()
               .padding(8.dp)
               .border(1.dp, statusColor, RoundedCornerShape(4.dp))
               .padding(8.dp),
       ) {
           Row(
               modifier = Modifier.fillMaxWidth(),
               horizontalArrangement = Arrangement.SpaceBetween,
           ) {
               Text("${item.entityType}/${item.entityId}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
               Badge(backgroundColor = statusColor) {
                   Text(item.status.uppercase(), fontSize = 10.sp, color = Color.White)
               }
           }
           
           Text("Operation: ${item.operation}", fontSize = 10.sp)
           Text("Retries: ${item.retryCount}", fontSize = 10.sp)
           
           if (item.errorMessage != null) {
               Text(
                   "Error: ${item.errorMessage}",
                   fontSize = 10.sp,
                   color = Color.Red,
               )
           }
           
           if (item.lastRetryAt != null) {
               val relativeTime = formatRelativeTime(item.lastRetryAt!!)
               Text("Last retry: $relativeTime ago", fontSize = 9.sp, color = Color.Gray)
           }
       }
   }
   
   @Composable
   fun SyncAuditEntryRow(entry: SyncAuditEntry) {
       Column(
           modifier = Modifier
               .fillMaxWidth()
               .padding(8.dp)
               .background(Color(0xFFF5F5F5))
               .padding(8.dp),
       ) {
           Row(
               modifier = Modifier.fillMaxWidth(),
               horizontalArrangement = Arrangement.SpaceBetween,
           ) {
               Text(entry.action, fontSize = 12.sp, fontWeight = FontWeight.Bold)
               Text(formatRelativeTime(entry.timestamp), fontSize = 10.sp, color = Color.Gray)
           }
           
           Text(entry.details, fontSize = 9.sp)
       }
   }
   ```

2. Create ViewModel:
   ```kotlin
   @HiltViewModel
   class SyncQueueDebugViewModel @Inject constructor(
       private val syncQueueDao: SyncQueueDao,
       private val syncAuditDao: SyncAuditDao,
   ) : ViewModel() {
       val queueItems = syncQueueDao.observeAllItems()
           .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
       
       val auditEntries = syncAuditDao.observeRecentEntries(limit = 100)
           .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
   }
   ```

3. Add to navigation menu (for internal use):
   ```kotlin
   // In debug menu
   MenuItem(
       label = "Sync Queue",
       action = { navigateTo("debug/sync-queue") }
   )
   ```

**Acceptance Criteria:**
- ✅ Debug screen shows all queue items with status, retries, errors
- ✅ Audit log shows stuck item resets
- ✅ Relative time ("5 minutes ago") displayed
- ✅ Manual test: Open debug screen → complete trip → see queue item appear → watch status change

**Effort:** 2–3 hours

---

## WEB (REACT/TYPESCRIPT) TASKS

### P0: Data Correctness (Week 1)

#### Task W1: Fix Mirror Status Override Logic (P0)

**Problem:**
- Mirror status override regression: Firestore listener reverts trip from "completed" to stale "in_progress"
- 2-second merge window allows stale Firestore to override fresh backend
- No distinction between "fresh backend" and "stale cache"

**Files:**
- `web/src/pages/office/Dashboard.tsx:36–52`
- `web/src/pages/office/TripDetail.tsx:47–68`

**Solution:**

1. Update `shouldApplyMirrorStatus()` in `Dashboard.tsx`:
   ```typescript
   function shouldApplyMirrorStatus(
       trip: Trip,
       mirror: { status: TripStatus | null; sourceUpdatedAt: string | null } | undefined
   ): boolean {
       if (!mirror?.status) {
           return false
       }
       
       // NEVER override if backend is in a terminal state
       if ((trip.status === 'completed' || trip.status === 'cancelled') && mirror.status !== trip.status) {
           return false
       }
       
       // NEW: Restrict to safe, read-only statuses only
       const isSafeStatusForMirror = 
           trip.status === 'scheduled' || 
           trip.status === 'pending'
       
       if (!isSafeStatusForMirror) {
           // Don't apply mirror for in-progress or modified states
           return false
       }
       
       const backendUpdatedAtMs = parseEpochMs(trip.updated_at)
       const mirrorUpdatedAtMs = parseEpochMs(mirror.sourceUpdatedAt)
       
       if (backendUpdatedAtMs === null || mirrorUpdatedAtMs === null) {
           return false
       }
       
       // Wider window (5 seconds) for read-only statuses
       return mirrorUpdatedAtMs >= backendUpdatedAtMs - 5000
   }
   ```

2. Update `shouldApplyMirrorStatus()` in `TripDetail.tsx` (same logic):
   ```typescript
   function shouldApplyMirrorStatus(
       backendStatus: string | undefined,
       backendUpdatedAt: string | undefined,
       mirrorStatus: string | null,
       mirrorSourceUpdatedAt: string | null,
   ): boolean {
       if (!backendStatus || !mirrorStatus) {
           return false
       }
       
       // NEVER override if backend is in terminal state
       if ((backendStatus === 'completed' || backendStatus === 'cancelled') && mirrorStatus !== backendStatus) {
           return false
       }
       
       // NEW: Restrict to safe statuses
       const isSafeStatusForMirror = 
           backendStatus === 'scheduled' || 
           backendStatus === 'pending'
       
       if (!isSafeStatusForMirror) {
           return false
       }
       
       const backendUpdatedAtMs = parseEpochMs(backendUpdatedAt)
       const mirrorUpdatedAtMs = parseEpochMs(mirrorSourceUpdatedAt)
       
       if (backendUpdatedAtMs === null || mirrorUpdatedAtMs === null) {
           return false
       }
       
       return mirrorUpdatedAtMs >= backendUpdatedAtMs - 5000
   }
   ```

3. Add log statement for debugging:
   ```typescript
   function shouldApplyMirrorStatus(...): boolean {
       // ... logic ...
       
       if (shouldApply) {
           console.debug(`[MIRROR] Applying mirror status: ${trip.status} → ${mirror.status}`)
       } else {
           console.debug(`[MIRROR] Skipping mirror: backend=${trip.status} mirror=${mirror.status} safe=${isSafeStatusForMirror}`)
       }
       
       return shouldApply
   }
   ```

**Acceptance Criteria:**
- ✅ Mirror cannot override "completed" or "cancelled" status
- ✅ Mirror cannot override "in_progress" status
- ✅ Mirror only applies to "scheduled" or "pending" (safe statuses)
- ✅ Merge window is 5 seconds (wider than before for read-only statuses)
- ✅ Manual test: Complete trip → verify mirror doesn't override → UI stays "completed"

**Effort:** 1–2 hours

---

#### Task W2: Add Immediate Refetch After Trip Completion (P0)

**Problem:**
- `completeMutation` invalidates cache but doesn't force refetch immediately
- UI shows stale "in_progress" for 30 seconds (React Query staleTime)
- No visual sync indicator

**Files:**
- `web/src/pages/office/TripDetail.tsx:179–183`

**Solution:**

1. Update `completeMutation` in `TripDetail.tsx`:
   ```typescript
   const completeMutation = useMutation({
       mutationFn: () => completeTrip(Number(id)),
       onSuccess: () => {
           // Immediately refetch trip details (don't just invalidate)
           queryClient.refetchQueries({
               queryKey: ['trip', id],
               type: 'active',
           })
           
           // Also invalidate related queries
           queryClient.invalidateQueries({ queryKey: ['trips'] })
           queryClient.invalidateQueries({ queryKey: ['settlement', id] })
           queryClient.invalidateQueries({ queryKey: ['pending-settlements'] })
           queryClient.invalidateQueries({ queryKey: ['settlements'] })
       },
       onError: (err) => {
           setActionError(extractErrorMsg(err, "Erreur lors de la clôture."))
       },
   })
   ```

2. Add sync status tracking to component:
   ```typescript
   const [isSyncing, setIsSyncing] = useState(false)
   
   useEffect(() => {
       if (completeMutation.isPending) {
           setIsSyncing(true)
       } else if (isLoading === false) {
           // After refetch completes
           setIsSyncing(false)
       }
   }, [completeMutation.isPending, isLoading])
   ```

3. Show sync badge in UI:
   ```typescript
   <div className="flex items-center gap-2">
       <StatusBadge status={effectiveTripStatus} type="trip" />
       {isSyncing && (
           <span className="inline-flex items-center gap-1 text-xs text-brand-300">
               <div className="h-2 w-2 animate-spin rounded-full border-t border-brand-300"></div>
               Syncing...
           </span>
       )}
   </div>
   ```

**Acceptance Criteria:**
- ✅ `refetchQueries()` called after completion
- ✅ Trip data refetched immediately (not waiting 30s for staleTime)
- ✅ "Syncing..." badge shown while refetching
- ✅ Manual test: Complete trip → see "Syncing..." badge → disappears when data refreshes

**Effort:** 1–2 hours

---

#### Task W3: Add Delay Before Settlement Query (P0)

**Problem:**
- Settlement query fires immediately when trip status becomes "completed"
- Backend may not have created settlement yet (mobile still syncing tickets)
- 404 error shown to user

**Files:**
- `web/src/pages/office/TripDetail.tsx:119–124`

**Solution:**

1. Add settlement query delay:
   ```typescript
   const [settlementQueryEnabled, setSettlementQueryEnabled] = useState(false)
   
   useEffect(() => {
       if (effectiveStatus === 'completed' && canAccessSettlementSection) {
           // Wait 2 seconds before querying settlement
           // This gives backend time to create it
           const timer = setTimeout(() => {
               setSettlementQueryEnabled(true)
           }, 2000)
           
           return () => clearTimeout(timer)
       } else {
           setSettlementQueryEnabled(false)
       }
   }, [effectiveStatus, canAccessSettlementSection])
   
   const {
       data: settlement,
       error: settlementError,
       isLoading: isSettlementLoading,
   } = useQuery({
       queryKey: ['settlement', id],
       queryFn: () => getSettlement(Number(id)),
       enabled: !!id && !!trip && settlementQueryEnabled,  // Use delay flag
       retry: (failureCount, error) => {
           // On 404, retry with backoff
           const err = error as any
           if (err?.response?.status === 404) {
               return failureCount < 3  // Max 3 retries
           }
           return failureCount < 1  // Other errors: single retry
       },
       retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),  // Exponential backoff
   })
   ```

2. Add loading state for settlement:
   ```typescript
   {effectiveStatus === 'completed' && !settlementQueryEnabled && (
       <div className="rounded-lg border border-brand-500/20 bg-[#137fec]/10 p-4 text-sm text-brand-300">
           Settlement will load in a moment...
       </div>
   )}
   ```

**Acceptance Criteria:**
- ✅ Settlement query waits 2 seconds after trip marked "completed"
- ✅ Retry logic handles 404 with exponential backoff (max 3 retries)
- ✅ UI shows "Settlement will load..." message during delay
- ✅ Manual test: Complete trip → see delay message → settlement appears after 2–3 seconds

**Effort:** 1–2 hours

---

#### Task W4: Add Timestamp-Aware Merge for Firestore Listeners (P0)

**Problem:**
- `onSnapshot()` direct overwrite without timestamp comparison
- Stale Firestore listener clobbers fresh React Query state
- No conflict resolution

**Files:**
- `web/src/hooks/useTripMirrorData.ts:182–200`

**Solution:**

1. Update listener with timestamp merge:
   ```typescript
   function useTripMirrorCollection<T>(
       tripId: number,
       collectionName: string,
       mapper: (docData: DocumentData) => T | null,
   ): MirrorHookState<T> {
       const [state, setState] = useState<MirrorHookState<T>>({
           data: [],
           isLoading: true,
           error: null,
       })
       
       useEffect(() => {
           if (!tripId || !Number.isFinite(tripId)) {
               setState({ data: [], isLoading: false, error: null })
               return
           }
           
           let unsubscribe: (() => void) | null = null
           let active = true
           let lastUpdateTimestamp = 0  // Track last update time
           
           const start = async () => {
               setState((prev) => ({ ...prev, isLoading: true, error: null }))
               const sessionReady = await ensureFirebaseSession()
               const firestore = getFirebaseFirestore()
               
               if (!active) return
               
               if (!sessionReady || !firestore) {
                   setState({ data: [], isLoading: false, error: 'Firebase non disponible.' })
                   return
               }
               
               const mirrorQuery = query(
                   collection(firestore, collectionName),
                   where('trip_id', '==', tripId),
                   where('is_deleted', '==', false),
                   orderBy('source_created_at', 'desc'),
                   limit(1000),
               )
               
               unsubscribe = onSnapshot(
                   mirrorQuery,
                   (snapshot) => {
                       if (!active) return
                       
                       // NEW: Timestamp-aware merge
                       const now = Date.now()
                       const docUpdateTimestamp = parseEpochMs(snapshot.metadata.fromCache ? '1970' : new Date().toISOString()) || now
                       
                       // If listener is very stale (from cache, > 1 second old), skip update
                       if (now - lastUpdateTimestamp < 1000 && docUpdateTimestamp < lastUpdateTimestamp) {
                           console.debug(`[MIRROR] Skipping stale listener update for ${collectionName}`)
                           return
                       }
                       
                       const mapped = snapshot.docs
                           .map((document) => {
                               const data = mapper(document.data())
                               const ts = parseEpochMs(document.data().source_updated_at) || 0
                               return { data, ts }
                           })
                           .filter((item): item is any => item.data !== null)
                           .sort((a, b) => b.ts - a.ts)  // Sort by timestamp, newest first
                           .map(item => item.data)
                       
                       lastUpdateTimestamp = docUpdateTimestamp
                       setState({ data: mapped, isLoading: false, error: null })
                       
                       console.debug(`[MIRROR] Updated ${collectionName}: ${mapped.length} items`)
                   },
                   (error) => {
                       console.warn(`[FIREBASE] ${collectionName} listener failed.`, error)
                       if (active) {
                           setState({ data: [], isLoading: false, error: 'Erreur Firebase temps réel.' })
                       }
                   },
               )
           }
           
           start()
           
           return () => {
               active = false
               unsubscribe?.()
           }
       }, [tripId, collectionName, mapper])
       
       return state
   }
   ```

2. Add helper function:
   ```typescript
   function parseEpochMs(value: string | null | undefined): number | null {
       if (!value) return null
       const timestamp = Date.parse(value)
       return Number.isFinite(timestamp) ? timestamp : null
   }
   ```

**Acceptance Criteria:**
- ✅ Listener skips updates that are significantly older than previous update
- ✅ Documents sorted by `source_updated_at` before state update
- ✅ Stale Firestore data is ignored
- ✅ Console logs show "[MIRROR]" messages for debugging
- ✅ Manual test: Create ticket → verify timestamp → mock stale listener → verify update skipped

**Effort:** 2–3 hours

---

### P1: Data Integrity (Week 2)

#### Task W5: Validate Cargo Ticket Backend Before Firestore (P1)

**Problem:**
- Cargo tickets written to Firestore without backend API confirmation
- If backend returns 403 (permission denied), queue doesn't know
- Ticket appears in Firestore but doesn't exist in backend DB

**Files:**
- `web/src/sync/engine.ts` (inferred, not fully shown)
- `web/src/sync/operationalSync.ts`
- `web/src/api/cargo.ts` (need to verify or create validation endpoint)

**Solution:**

1. Create cargo validation endpoint on backend (if not exists):
   ```python
   # backend/api/views/cargo.py (NEW ENDPOINT)
   @api_view(['POST'])
   @permission_classes([IsAuthenticated])
   def validate_cargo_ticket_creation(request):
       """
       Validate if current user can create a cargo ticket.
       Used by web app to check permission before Firestore mirror write.
       """
       try:
           user = request.user
           
           # Check permission: only office_staff (not cargo department)
           if user.role == 'office_staff' and user.department == 'cargo':
               return Response(
                   {'error': 'Cargo staff cannot create tickets'},
                   status=status.HTTP_403_FORBIDDEN,
               )
           
           # Check if trip exists and user can access it
           trip_id = request.data.get('trip_id')
           if trip_id:
               try:
                   trip = Trip.objects.get(id=trip_id)
               except Trip.DoesNotExist:
                   return Response(
                       {'error': 'Trip not found'},
                       status=status.HTTP_404_NOT_FOUND,
                   )
           
           return Response({'valid': True}, status=status.HTTP_200_OK)
       except Exception as e:
           return Response(
               {'error': str(e)},
               status=status.HTTP_500_INTERNAL_SERVER_ERROR,
           )
   ```

2. Add API client method in `web/src/api/cargo.ts`:
   ```typescript
   export async function validateCargoTicketCreation(tripId: number): Promise<boolean> {
       try {
           const response = await client.post(`/api/cargo/validate/`, { trip_id: tripId })
           return response.status === 200
       } catch (error) {
           console.error('Cargo validation failed:', error)
           return false
       }
   }
   ```

3. Update sync engine to validate before Firestore write:
   ```typescript
   // web/src/sync/engine.ts
   async function syncItemToFirestore(item: SyncQueueItem): Promise<boolean> {
       try {
           const { entityType, payload } = item
           
           // NEW: Validate cargo tickets before Firestore write
           if (entityType === 'cargo_ticket' && item.operation === 'upsert') {
               const cargoPayload = payload as CargoTicket
               const isValid = await validateCargoTicketCreation(cargoPayload.trip)
               
               if (!isValid) {
                   // Terminal error: mark as failed, don't write Firestore
                   await markSyncItemTerminal(
                       item.id,
                       403,
                       'Permission denied: cannot create cargo ticket'
                   )
                   return false
               }
           }
           
           // Proceed with Firestore write
           const mapped = mapEntityUpsertDocument(
               entityType,
               payload,
               item.opId,
               item.sourceUpdatedAt,
           )
           
           const docRef = firestore.collection(COLLECTION_BY_ENTITY[entityType]).doc(String(item.entityId))
           await firestore.runTransaction(async (tx) => {
               tx.set(docRef, mapped, { merge: true })
           })
           
           return true
       } catch (error) {
           console.error('Failed to sync to Firestore:', error)
           return false
       }
   }
   ```

4. Update `queueCargoTicketUpsert()` to validate before enqueue:
   ```typescript
   export async function queueCargoTicketUpsert(ticket: CargoTicket): Promise<void> {
       // Validate permission before queueing
       const isValid = await validateCargoTicketCreation(ticket.trip)
       if (!isValid) {
           throw new Error('Permission denied: cannot create cargo ticket')
       }
       
       const opId = `cargo_ticket:${ticket.id}:upsert:${ticket.updated_at}`
       
       await enqueueSyncRecord({
           entityType: 'cargo_ticket',
           entityId: String(ticket.id),
           operation: 'upsert',
           payload: ticket,
           sourceUpdatedAt: resolveSourceUpdatedAt(ticket),
           opId,
           dedupeKey: opId,
       })
       
       requestSyncDrain(0)
   }
   ```

**Acceptance Criteria:**
- ✅ Backend validation endpoint created
- ✅ Web validates permission before Firestore write
- ✅ 403 response marks queue item "terminal_failed", doesn't retry
- ✅ No Firestore write on permission denial
- ✅ Manual test: Cargo staff attempts create → validation fails → no Firestore write

**Effort:** 3–4 hours

---

#### Task W6: Implement Exponential Backoff for Terminal Errors (P1)

**Problem:**
- Non-transient errors (403, 400) retry forever, clogs queue
- No distinction between retryable and terminal errors
- Queue items can get stuck indefinitely

**Files:**
- `web/src/sync/queue.ts`
- `web/src/sync/engine.ts`

**Solution:**

1. Update `SyncQueueItem` type to include error tracking:
   ```typescript
   interface SyncQueueItem {
       id: string
       entityType: SyncEntityType
       entityId: string
       operation: 'upsert' | 'delete'
       payload: unknown
       sourceUpdatedAt: string
       opId: string
       dedupeKey: string
       status: 'pending' | 'syncing' | 'synced' | 'failed' | 'terminal'  // NEW: terminal
       retryCount: number
       lastRetryAt: number | null
       terminalErrorCode: number | null  // NEW: store terminal error code
       errorMessage: string | null
       createdAt: number
       updatedAt: number
   }
   ```

2. Add terminal error classification:
   ```typescript
   // web/src/sync/errorClassifier.ts (new)
   export const TERMINAL_HTTP_CODES = [400, 401, 403, 404]
   export const TRANSIENT_HTTP_CODES = [429, 500, 502, 503, 504]
   
   export function isTerminalError(httpCode: number | null): boolean {
       return TERMINAL_HTTP_CODES.includes(httpCode ?? 0)
   }
   
   export function isRetryableError(httpCode: number | null): boolean {
       return TRANSIENT_HTTP_CODES.includes(httpCode ?? 0) || httpCode === null
   }
   ```

3. Update queue handling:
   ```typescript
   // web/src/sync/queue.ts
   
   async function markSyncItemTerminal(
       itemId: string,
       httpCode: number,
       errorMessage: string,
   ): Promise<void> {
       await db.table('sync_queue').update(itemId, {
           status: 'terminal',
           terminalErrorCode: httpCode,
           errorMessage,
           updatedAt: Date.now(),
       })
   }
   
   async function handleSyncFailure(
       item: SyncQueueItem,
       httpCode: number | null,
       errorMessage: string,
   ): Promise<void> {
       // Check if error is terminal
       if (isTerminalError(httpCode)) {
           await markSyncItemTerminal(item.id, httpCode || 0, errorMessage)
           console.warn(`[SYNC] Terminal error for item ${item.id}: ${errorMessage}`)
           return
       }
       
       // Retryable error: exponential backoff
       const newRetryCount = item.retryCount + 1
       const maxRetries = 5
       
       if (newRetryCount >= maxRetries) {
           await markSyncItemTerminal(
               item.id,
               httpCode || 0,
               `Max retries exceeded: ${errorMessage}`
           )
           console.warn(`[SYNC] Max retries for item ${item.id}`)
       } else {
           // Exponential backoff: 1s, 2s, 4s, 8s, 16s
           const delayMs = 1000 * (1 << (newRetryCount - 1))
           
           await db.table('sync_queue').update(item.id, {
               status: 'pending',
               retryCount: newRetryCount,
               lastRetryAt: Date.now(),
               errorMessage,
               updatedAt: Date.now(),
           })
           
           console.debug(`[SYNC] Retry ${newRetryCount} for item ${item.id} in ${delayMs}ms`)
           
           // Schedule retry
           requestSyncDrain(delayMs)
       }
   }
   ```

4. Update engine to use new classification:
   ```typescript
   // web/src/sync/engine.ts
   
   async function processQueueItem(item: SyncQueueItem): Promise<boolean> {
       try {
           const mapped = mapEntityUpsertDocument(...)
           const response = await firestore.runTransaction(...)
           return true
       } catch (error) {
           const httpCode = (error as any)?.response?.status || null
           const errorMessage = (error as any)?.response?.data?.error || error.message
           
           await handleSyncFailure(item, httpCode, errorMessage)
           return false
       }
   }
   ```

**Acceptance Criteria:**
- ✅ Terminal errors (403, 400) marked "terminal", not retried
- ✅ Transient errors (429, 5xx) retried with exponential backoff
- ✅ Max 5 retries before terminal failure
- ✅ Log shows "Terminal error" vs "Retry N" classification
- ✅ Manual test: Cargo validation 403 → queue item marked "terminal" → not retried

**Effort:** 2–3 hours

---

#### Task W7: Add Backend Audit Log Endpoint (P1)

**Problem:**
- No server-side sync audit trail (IndexedDB only)
- Regulatory/compliance gap
- Can't inspect web sync queue from backend

**Files:**
- `backend/api/models/sync.py` (new)
- `backend/api/views/sync_views.py` (new)
- `web/src/sync/engine.ts`

**Solution:**

1. Create backend model to track sync operations:
   ```python
   # backend/api/models/sync.py (NEW)
   from django.db import models
   
   class WebSyncAuditLog(models.Model):
       """Audit log for web sync operations"""
       
       OPERATION_CHOICES = [
           ('upsert', 'Upsert'),
           ('delete', 'Delete'),
       ]
       
       STATUS_CHOICES = [
           ('synced', 'Synced'),
           ('failed', 'Failed'),
           ('terminal', 'Terminal'),
       ]
       
       web_admin = models.ForeignKey('api.User', on_delete=models.SET_NULL, null=True)
       entity_type = models.CharField(max_length=50)  # "trip", "cargo_ticket", etc.
       entity_id = models.IntegerField()
       operation = models.CharField(max_length=10, choices=OPERATION_CHOICES)
       status = models.CharField(max_length=20, choices=STATUS_CHOICES)
       error_code = models.IntegerField(null=True, blank=True)
       error_message = models.TextField(null=True, blank=True)
       timestamp = models.DateTimeField(auto_now_add=True)
       
       class Meta:
           indexes = [
               models.Index(fields=['entity_type', 'entity_id']),
               models.Index(fields=['web_admin', 'timestamp']),
           ]
       
       def __str__(self):
           return f"{self.entity_type}/{self.entity_id} {self.operation} ({self.status})"
   ```

2. Create API endpoint:
   ```python
   # backend/api/views/sync_views.py (NEW ENDPOINT)
   from rest_framework import status
   from rest_framework.decorators import api_view, permission_classes
   from rest_framework.permissions import IsAuthenticated
   from rest_framework.response import Response
   from .models import WebSyncAuditLog
   
   @api_view(['POST'])
   @permission_classes([IsAuthenticated])
   def log_web_sync_operation(request):
       """
       Log a web sync operation to audit trail.
       Called by web app after successful Firestore write.
       """
       try:
           data = request.data
           
           audit_entry = WebSyncAuditLog.objects.create(
               web_admin=request.user,
               entity_type=data.get('entity_type'),
               entity_id=data.get('entity_id'),
               operation=data.get('operation'),
               status=data.get('status'),
               error_code=data.get('error_code'),
               error_message=data.get('error_message'),
           )
           
           return Response(
               {
                   'id': audit_entry.id,
                   'timestamp': audit_entry.timestamp.isoformat(),
               },
               status=status.HTTP_201_CREATED,
           )
       except Exception as e:
           return Response(
               {'error': str(e)},
               status=status.HTTP_500_INTERNAL_SERVER_ERROR,
           )
   ```

3. Add URL route:
   ```python
   # backend/api/urls.py
   urlpatterns = [
       # ... existing URLs ...
       path('sync/audit-log/', log_web_sync_operation, name='web_sync_audit'),
   ]
   ```

4. Call from web sync engine:
   ```typescript
   // web/src/sync/engine.ts
   
   async function logSyncToBackend(
       entityType: SyncEntityType,
       entityId: string,
       operation: 'upsert' | 'delete',
       status: 'synced' | 'failed' | 'terminal',
       errorCode: number | null = null,
       errorMessage: string | null = null,
   ): Promise<void> {
       try {
           await client.post('/api/sync/audit-log/', {
               entity_type: entityType,
               entity_id: entityId,
               operation,
               status,
               error_code: errorCode,
               error_message: errorMessage,
           })
       } catch (error) {
           // Don't fail sync if audit logging fails
           console.warn('[SYNC] Failed to log to backend:', error)
       }
   }
   
   async function processQueueItem(item: SyncQueueItem): Promise<boolean> {
       try {
           // ... sync to Firestore ...
           
           // Log success
           await logSyncToBackend(item.entityType, item.entityId, item.operation, 'synced')
           return true
       } catch (error) {
           const httpCode = (error as any)?.response?.status || null
           const errorMessage = (error as any)?.response?.data?.error || error.message
           
           // Log failure
           await logSyncToBackend(
               item.entityType,
               item.entityId,
               item.operation,
               'failed',
               httpCode,
               errorMessage,
           )
           
           return false
       }
   }
   ```

**Acceptance Criteria:**
- ✅ `WebSyncAuditLog` model created with indexes
- ✅ `/api/sync/audit-log/` endpoint POST works
- ✅ Web calls endpoint after Firestore sync
- ✅ Audit entries include entity, operation, status, error code/message
- ✅ Manual test: Create cargo ticket → verify audit entry in backend DB

**Effort:** 3–4 hours

---

#### Task W8: Add Server-Side Sync Status Endpoint (P1)

**Problem:**
- Backend can't report sync queue status (IndexedDB only on client)
- Web UI can't show "X pending items" badge
- No visibility into browser sync health

**Files:**
- `backend/api/views/sync_views.py`
- `web/src/api/sync.ts` (new)
- `web/src/hooks/useSyncStatus.ts` (new)

**Solution:**

1. Add backend endpoint:
   ```python
   # backend/api/views/sync_views.py (NEW ENDPOINT)
   
   @api_view(['GET'])
   @permission_classes([IsAuthenticated])
   def get_web_sync_queue_status(request):
       """
       Report sync queue status from web.
       Returns counts of pending, syncing, failed items.
       """
       try:
           pending_count = WebSyncAuditLog.objects.filter(
               web_admin=request.user,
               status='synced',
               timestamp__gte=timezone.now() - timedelta(hours=1),
           ).count()
           
           failed_count = WebSyncAuditLog.objects.filter(
               web_admin=request.user,
               status='failed',
               timestamp__gte=timezone.now() - timedelta(hours=1),
           ).count()
           
           terminal_count = WebSyncAuditLog.objects.filter(
               web_admin=request.user,
               status='terminal',
               timestamp__gte=timezone.now() - timedelta(hours=1),
           ).count()
           
           oldest_pending = WebSyncAuditLog.objects.filter(
               web_admin=request.user,
               status='synced',
           ).order_by('timestamp').first()
           
           return Response({
               'pending_count': pending_count,
               'failed_count': failed_count,
               'terminal_count': terminal_count,
               'oldest_pending_at': oldest_pending.timestamp.isoformat() if oldest_pending else None,
           })
       except Exception as e:
           return Response(
               {'error': str(e)},
               status=status.HTTP_500_INTERNAL_SERVER_ERROR,
           )
   ```

2. Add web API client:
   ```typescript
   // web/src/api/sync.ts (NEW)
   import client from './client'
   
   export interface SyncQueueStatus {
       pending_count: number
       failed_count: number
       terminal_count: number
       oldest_pending_at: string | null
   }
   
   export async function getSyncQueueStatus(): Promise<SyncQueueStatus> {
       const response = await client.get<SyncQueueStatus>('/api/sync/queue-status/')
       return response.data
   }
   ```

3. Create hook:
   ```typescript
   // web/src/hooks/useSyncStatus.ts (NEW)
   import { useQuery } from '@tanstack/react-query'
   import { getSyncQueueStatus } from '../api/sync'
   
   export function useSyncStatus() {
       return useQuery({
           queryKey: ['sync-status'],
           queryFn: getSyncQueueStatus,
           refetchInterval: 5000,  // Poll every 5 seconds
           retry: false,
       })
   }
   ```

4. Add to UI:
   ```typescript
   // web/src/components/SyncStatusBadge.tsx (NEW)
   import { useSyncStatus } from '../hooks/useSyncStatus'
   
   export function SyncStatusBadge() {
       const { data: syncStatus } = useSyncStatus()
       
       if (!syncStatus) return null
       
       const totalIssues = syncStatus.failed_count + syncStatus.terminal_count
       
       if (totalIssues > 0) {
           return (
               <div className="inline-flex items-center gap-1 px-2 py-1 rounded text-xs bg-red-500/10 text-red-400 border border-red-500/20">
                   ✗ {totalIssues} sync issue(s)
               </div>
           )
       }
       
       if (syncStatus.pending_count > 0) {
           return (
               <div className="inline-flex items-center gap-1 px-2 py-1 rounded text-xs bg-yellow-500/10 text-yellow-400 border border-yellow-500/20">
                   ⟳ {syncStatus.pending_count} pending
               </div>
           )
       }
       
       return (
           <div className="inline-flex items-center gap-1 px-2 py-1 rounded text-xs bg-green-500/10 text-green-400 border border-green-500/20">
               ✓ In sync
           </div>
       )
   }
   ```

5. Add to Dashboard header:
   ```typescript
   // web/src/pages/office/Dashboard.tsx
   import { SyncStatusBadge } from '../../components/SyncStatusBadge'
   
   export function OfficeDashboard() {
       return (
           <div className="space-y-6">
               <section className="...">
                   <div className="flex items-center justify-between">
                       <h1>Dashboard</h1>
                       <SyncStatusBadge />
                   </div>
               </section>
               {/* rest of dashboard */}
           </div>
       )
   }
   ```

**Acceptance Criteria:**
- ✅ Backend endpoint returns pending/failed/terminal counts
- ✅ Counts updated from web sync audit log (last 1 hour)
- ✅ Web polls endpoint every 5 seconds
- ✅ SyncStatusBadge shows status (✓ In sync, ⟳ pending, ✗ issues)
- ✅ Manual test: Create cargo ticket → verify badge shows status → watch counts update

**Effort:** 2–3 hours

---

### P2: UX & Optimization (Week 3)

#### Task W9: Add Explicit Sync Status Indicators (P2)

**Problem:**
- Users don't see visual feedback during sync operations
- No pending count visibility in UI
- "Syncing..." indicator helps users understand state

**Files:**
- `web/src/pages/office/Dashboard.tsx`
- `web/src/pages/office/TripDetail.tsx`
- `web/src/pages/office/TripList.tsx`

**Solution:**

Already partially covered in Task W8 (SyncStatusBadge). Additional improvements:

1. Add trip-level sync indicator:
   ```typescript
   // web/src/pages/office/TripList.tsx
   
   function TripRow({ trip }: { trip: Trip }) {
       const { data: syncStatus } = useSyncStatus()
       const isTripSyncing = syncStatus?.pending_count ?? 0 > 0
       
       return (
           <tr className="...">
               <td className="...">{trip.id}</td>
               <td className="...">
                   <div className="flex items-center gap-2">
                       <StatusBadge status={trip.status} type="trip" />
                       {isTripSyncing && (
                           <span className="inline-flex items-center gap-1 text-xs text-brand-300">
                               <div className="h-1.5 w-1.5 animate-spin rounded-full border border-brand-300 border-r-transparent"></div>
                               Syncing...
                           </span>
                       )}
                   </div>
               </td>
               {/* rest of row */}
           </tr>
       )
   }
   ```

2. Add pending items debug view (optional):
   ```typescript
   // web/src/pages/admin/SyncDebug.tsx (NEW, optional)
   import { useQuery } from '@tanstack/react-query'
   import { getSyncQueueStatus } from '../../api/sync'
   
   export function SyncDebugPage() {
       const { data: status } = useQuery({
           queryKey: ['sync-status'],
           queryFn: getSyncQueueStatus,
           refetchInterval: 2000,
       })
       
       if (!status) return null
       
       return (
           <div className="space-y-4 p-6">
               <h1 className="text-2xl font-bold">Sync Queue Status</h1>
               
               <div className="grid grid-cols-3 gap-4">
                   <div className="rounded-lg border border-brand-500/20 bg-brand-500/5 p-4">
                       <p className="text-sm text-text-muted">Pending Items</p>
                       <p className="text-3xl font-bold">{status.pending_count}</p>
                   </div>
                   
                   <div className="rounded-lg border border-yellow-500/20 bg-yellow-500/5 p-4">
                       <p className="text-sm text-text-muted">Failed Items</p>
                       <p className="text-3xl font-bold">{status.failed_count}</p>
                   </div>
                   
                   <div className="rounded-lg border border-red-500/20 bg-red-500/5 p-4">
                       <p className="text-sm text-text-muted">Terminal Items</p>
                       <p className="text-3xl font-bold">{status.terminal_count}</p>
                   </div>
               </div>
               
               {status.oldest_pending_at && (
                   <p className="text-sm text-text-muted">
                       Oldest pending: {new Date(status.oldest_pending_at).toLocaleString()}
                   </p>
               )}
           </div>
       )
   }
   ```

**Acceptance Criteria:**
- ✅ SyncStatusBadge visible on Dashboard
- ✅ Trip rows show "Syncing..." indicator when pending
- ✅ Sync debug page shows counts (optional)
- ✅ Manual test: Create cargo ticket → Dashboard shows sync status badge

**Effort:** 1–2 hours

---

#### Task W10: Debounce Firestore Listeners (P2)

**Problem:**
- Firestore listeners fire immediately on every change
- Can cause rapid state updates and flickering UI
- Debounce reduces unnecessary re-renders

**Files:**
- `web/src/hooks/useTripMirrorData.ts:182–200`

**Solution:**

1. Add debounce to listener:
   ```typescript
   function useTripMirrorCollection<T>(
       tripId: number,
       collectionName: string,
       mapper: (docData: DocumentData) => T | null,
   ): MirrorHookState<T> {
       const [state, setState] = useState<MirrorHookState<T>>({
           data: [],
           isLoading: true,
           error: null,
       })
       
       useEffect(() => {
           let unsubscribe: (() => void) | null = null
           let active = true
           let debounceTimer: NodeJS.Timeout | null = null  // NEW
           
           const start = async () => {
               setState((prev) => ({ ...prev, isLoading: true, error: null }))
               const sessionReady = await ensureFirebaseSession()
               const firestore = getFirebaseFirestore()
               
               if (!active || !sessionReady || !firestore) {
                   setState({ data: [], isLoading: false, error: 'Firebase non disponible.' })
                   return
               }
               
               const mirrorQuery = query(
                   collection(firestore, collectionName),
                   where('trip_id', '==', tripId),
                   where('is_deleted', '==', false),
                   orderBy('source_created_at', 'desc'),
                   limit(1000),
               )
               
               unsubscribe = onSnapshot(
                   mirrorQuery,
                   (snapshot) => {
                       if (!active) return
                       
                       // NEW: Debounce state update
                       if (debounceTimer) clearTimeout(debounceTimer)
                       
                       debounceTimer = setTimeout(() => {
                           const mapped = snapshot.docs
                               .map((document) => {
                                   const data = mapper(document.data())
                                   const ts = parseEpochMs(document.data().source_updated_at) || 0
                                   return { data, ts }
                               })
                               .filter((item): item is any => item.data !== null)
                               .sort((a, b) => b.ts - a.ts)
                               .map(item => item.data)
                           
                           setState({ data: mapped, isLoading: false, error: null })
                           console.debug(`[MIRROR] Updated ${collectionName}: ${mapped.length} items`)
                       }, 500)  // 500ms debounce
                   },
                   (error) => {
                       if (active) {
                           setState({ data: [], isLoading: false, error: 'Erreur Firebase temps réel.' })
                       }
                   },
               )
           }
           
           start()
           
           return () => {
               active = false
               if (debounceTimer) clearTimeout(debounceTimer)
               unsubscribe?.()
           }
       }, [tripId, collectionName, mapper])
       
       return state
   }
   ```

**Acceptance Criteria:**
- ✅ Listener update debounced by 500ms
- ✅ Rapid Firestore changes coalesced into single state update
- ✅ UI doesn't flicker on rapid listener fires
- ✅ Manual test: Create multiple tickets rapidly → verify UI smooth (not flickering)

**Effort:** 1–2 hours

---

#### Task W11: Add Client-Side Role Validation (P2)

**Problem:**
- No client-side role check before Firestore mirror write
- Cargo staff can create tickets locally without immediate permission check
- Defense-in-depth: validate before queue enqueue

**Files:**
- `web/src/sync/operationalSync.ts`
- `web/src/hooks/useAuth.ts`

**Solution:**

1. Add role check to sync functions:
   ```typescript
   // web/src/sync/operationalSync.ts
   
   import { useAuth } from '../hooks/useAuth'  // or pass user as param
   
   export async function queueCargoTicketUpsert(
       ticket: CargoTicket,
       userRole: string,
       userDepartment?: string,
   ): Promise<void> {
       // NEW: Client-side role check
       if (userRole === 'office_staff' && userDepartment === 'cargo') {
           const error = 'Permission denied: cargo staff cannot create tickets'
           console.warn(`[SYNC] ${error}`)
           throw new Error(error)
       }
       
       const opId = `cargo_ticket:${ticket.id}:upsert:${ticket.updated_at}`
       
       await enqueueSyncRecord({
           entityType: 'cargo_ticket',
           entityId: String(ticket.id),
           operation: 'upsert',
           payload: ticket,
           sourceUpdatedAt: resolveSourceUpdatedAt(ticket),
           opId,
           dedupeKey: opId,
       })
       
       requestSyncDrain(0)
   }
   ```

2. Update caller to pass user context:
   ```typescript
   // web/src/pages/cargo/CargoTickets.tsx or wherever creating cargo tickets
   
   const { user } = useAuth()
   
   const handleCreateCargoTicket = async (ticket: CargoTicket) => {
       try {
           await queueCargoTicketUpsert(
               ticket,
               user?.role || '',
               user?.department,
           )
       } catch (error) {
           setError(error.message)  // Show "Permission denied" in UI
       }
   }
   ```

**Acceptance Criteria:**
- ✅ `queueCargoTicketUpsert()` accepts user role/department
- ✅ Cargo staff role throws error before enqueue
- ✅ Error message "Permission denied" shown to user
- ✅ No queue item created on permission violation
- ✅ Manual test: Cargo staff attempts create → see "Permission denied" error

**Effort:** 1–2 hours

---

#### Task W12: Increase React Query Stale Time for Stable Sections (P2)

**Problem:**
- 30-second staleTime + Firestore latency = up to 60 seconds staleness
- For read-only/archived sections, longer cache is acceptable
- Reduces refetch load on backend

**Files:**
- `web/src/App.tsx`
- `web/src/pages/admin/Settlements.tsx`
- `web/src/pages/admin/Reports.tsx`

**Solution:**

1. Update App.tsx with context-aware staleTime:
   ```typescript
   // web/src/App.tsx
   
   const queryClient = new QueryClient({
       defaultOptions: {
           queries: {
               staleTime: 30000,  // 30 seconds (default for active sections)
               gcTime: 5 * 60000,  // 5 minutes (garbage collection time)
               refetchOnWindowFocus: true,  // Active refetch on focus
               retry: 1,
           },
       },
   })
   ```

2. Increase staleTime for read-only sections:
   ```typescript
   // web/src/pages/admin/Settlements.tsx
   
   const { data: settlements } = useQuery({
       queryKey: ['settlements'],
       queryFn: () => getSettlements(),
       staleTime: 5 * 60000,  // 5 minutes (read-only)
       refetchOnWindowFocus: false,  // No refetch on focus (not critical)
   })
   ```

3. Keep short staleTime for active sections:
   ```typescript
   // web/src/pages/office/TripList.tsx
   
   const { data: trips } = useQuery({
       queryKey: ['trips'],
       queryFn: () => getTrips(),
       staleTime: 30000,  // 30 seconds (active section)
       refetchOnWindowFocus: true,  // Refetch on focus
   })
   ```

4. Immediate refetch on critical mutations:
   ```typescript
   // Already done in Task W2, but verify:
   const completeMutation = useMutation({
       mutationFn: () => completeTrip(Number(id)),
       onSuccess: () => {
           // Force immediate refetch (don't wait for staleTime)
           queryClient.refetchQueries({
               queryKey: ['trip', id],
               type: 'active',
           })
       },
   })
   ```

**Acceptance Criteria:**
- ✅ Active sections (trips, dashboard) use 30s staleTime + refetch-on-focus
- ✅ Read-only sections (archived, reports) use 5m staleTime + no refetch-on-focus
- ✅ Critical mutations trigger immediate refetch
- ✅ Manual test: Switch tabs → verify stale data strategy

**Effort:** 1–2 hours

---

#### Task W13: Add Settlement Creation Delay & Retry (P2)

**Problem:**
- Settlement creation can fail if backend not ready (Task W3 addresses this)
- But add visual feedback about retry strategy

**Files:**
- `web/src/pages/office/TripDetail.tsx`

**Solution:**

1. Add settlement creation retry logic (already in Task W3):
   ```typescript
   const {
       data: settlement,
       ...
   } = useQuery({
       ...
       retry: (failureCount, error) => {
           const err = error as any
           if (err?.response?.status === 404) {
               return failureCount < 3
           }
           return failureCount < 1
       },
       retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
   })
   ```

2. Add retry feedback to UI:
   ```typescript
   {effectiveStatus === 'completed' && !isSettlementLoading && settlementError && !settlementMissing && (
       <div className="rounded-lg border border-status-warning/20 bg-status-warning/10 p-4 space-y-3">
           <div className="flex items-start gap-3">
               <AlertCircle className="w-5 h-5 text-yellow-600 dark:text-yellow-400 shrink-0 mt-0.5" />
               <div>
                   <p className="text-sm font-medium text-yellow-600 dark:text-yellow-400">
                       Settlement being created...
                   </p>
                   <p className="text-sm text-yellow-600 dark:text-yellow-400/80 mt-1">
                       This can take a few seconds. Please refresh in a moment.
                   </p>
               </div>
           </div>
           <Button
               size="sm"
               onClick={() => queryClient.refetchQueries({ queryKey: ['settlement', id] })}
           >
               Retry loading
           </Button>
       </div>
   )}
   ```

**Acceptance Criteria:**
- ✅ Settlement query retries 3 times on 404
- ✅ Exponential backoff: 1s, 2s, 4s
- ✅ UI shows "Settlement being created..." message
- ✅ Manual retry button available
- ✅ Manual test: Complete trip → wait for settlement → verify retry logic works

**Effort:** 1 hour

---

## BACKEND (PYTHON/DJANGO) TASKS

### Task B1: Add `sync_status` Field to Trip Model (P1)

**Problem:**
- Web doesn't know if mobile sync is complete before creating settlement
- Settlement creation fails with "TRIP_HAS_PENDING_SYNC" error

**Files:**
- `backend/api/models/trip.py`
- `backend/api/views/trip_views.py`

**Solution:**

1. Add field to Trip model:
   ```python
   class Trip(models.Model):
       # ... existing fields ...
       
       SYNC_STATUS_CHOICES = [
           ('pending', 'Pending'),
           ('syncing', 'Syncing'),
           ('synced', 'Synced'),
           ('failed', 'Failed'),
       ]
       
       sync_status = models.CharField(
           max_length=20,
           choices=SYNC_STATUS_CHOICES,
           default='pending',
           help_text='Mobile sync status for conductor-created data'
       )
   ```

2. Update settlement creation to check sync status:
   ```python
   # backend/api/views/trip_views.py
   
   def initiateSettlement(request, trip_id):
       trip = Trip.objects.get(id=trip_id)
       
       # Check if mobile sync is complete
       if trip.sync_status in ['pending', 'syncing']:
           return Response(
               {
                   'error': 'Trip sync still in progress',
                   'error_code': 'TRIP_HAS_PENDING_SYNC',
                   'sync_status': trip.sync_status,
               },
               status=status.HTTP_409_CONFLICT,
           )
       
       if trip.sync_status == 'failed':
           return Response(
               {
                   'error': 'Trip sync failed; unable to create settlement',
                   'error_code': 'TRIP_SYNC_FAILED',
               },
               status=status.HTTP_400_BAD_REQUEST,
           )
       
       # Proceed with settlement creation
       # ...
   ```

3. Update sync status when mobile completes sync:
   ```python
   # backend/api/views/sync_views.py
   
   @api_view(['POST'])
   @permission_classes([IsAuthenticated])
   def mark_trip_sync_complete(request):
       """Mark trip sync as complete after mobile finishes"""
       trip_id = request.data.get('trip_id')
       trip = Trip.objects.get(id=trip_id)
       
       trip.sync_status = 'synced'
       trip.save()
       
       return Response({'status': 'synced'})
   ```

**Acceptance Criteria:**
- ✅ Trip model has `sync_status` field
- ✅ Settlement creation blocked if sync_status != 'synced'
- ✅ Mobile worker marks trip "synced" after completing sync
- ✅ Manual test: Complete trip → verify sync_status tracks progress

**Effort:** 2–3 hours

---

### Task B2: Add Web Sync Audit Log Table & Endpoint (P1)

**Already covered in Task W7.**

---

## CROSS-SYSTEM INTEGRATION TASKS

### Task CS1: End-to-End Trip Completion → Settlement (P0)

**Problem:**
- Trip completion can fail to trigger settlement creation
- Data can diverge between mobile, web, backend, Firestore

**Solution:**

1. Ensure trip complete flow:
   - Mobile: completeTrip() → backend API → queue trip_status_backend → Firestore mirror
   - Backend: Update trip.status = "completed", set sync_status = "synced"
   - Web: Refetch trip → see "completed" → wait 2s → query settlement
   - Backend: Create settlement after sync complete

2. Add integration test (E2E):
   ```typescript
   // web/e2e/trip-completion-settlement.spec.ts
   import { test, expect } from '@playwright/test'
   
   test('Complete trip → settlement appears', async ({ page }) => {
       // Login as office admin
       await page.goto('/login')
       await page.fill('[name="phone"]', '+213123456789')
       await page.fill('[name="password"]', 'password')
       await page.click('[role="button"]:has-text("Login")')
       
       // Navigate to trip
       await page.goto('/office/trips/42')
       
       // Click complete button
       await page.click('[role="button"]:has-text("Clôturer")')
       
       // Verify trip status changes
       await expect(page.locator('[role="status"]')).toContainText('Completed')
       
       // Wait for settlement to load
       await page.waitForTimeout(3000)
       
       // Verify settlement section appears
       await expect(page.locator('[role="heading"]:has-text("Reglement")')).toBeVisible()
   })
   ```

**Effort:** 4–5 hours (cross-system coordination)

---

## TESTING & VALIDATION

### Task T1: Unit Tests for Error Classification (P2)

**Problem:**
- Error classification logic needs testing to prevent regressions

**Solution:**

1. Add unit tests for mobile:
   ```kotlin
   // mobile/app/src/test/java/com/souigat/mobile/util/ErrorClassifierTest.kt
   
   class ErrorClassifierTest {
       @Test
       fun classifyNetworkError() {
           val exception = SocketTimeoutException()
           assertEquals(ErrorClassifier.Category.NETWORK, ErrorClassifier.classify(exception))
       }
       
       @Test
       fun classifyTransientError() {
           assertEquals(ErrorClassifier.Category.TRANSIENT, ErrorClassifier.classify(429))
           assertEquals(ErrorClassifier.Category.TRANSIENT, ErrorClassifier.classify(500))
       }
       
       @Test
       fun classifyTerminalError() {
           assertEquals(ErrorClassifier.Category.TERMINAL, ErrorClassifier.classify(403))
           assertEquals(ErrorClassifier.Category.TERMINAL, ErrorClassifier.classify(400))
       }
   }
   ```

2. Add unit tests for web:
   ```typescript
   // web/src/sync/errorClassifier.test.ts
   
   import { isTerminalError, isRetryableError, TERMINAL_HTTP_CODES } from './errorClassifier'
   
   describe('ErrorClassifier', () => {
       it('classifies 403 as terminal', () => {
           expect(isTerminalError(403)).toBe(true)
           expect(isRetryableError(403)).toBe(false)
       })
       
       it('classifies 429 as retryable', () => {
           expect(isTerminalError(429)).toBe(false)
           expect(isRetryableError(429)).toBe(true)
       })
   })
   ```

**Effort:** 1–2 hours

---

### Task T2: E2E Tests for Sync Scenarios (P2)

**Problem:**
- Sync edge cases need end-to-end testing

**Solution:**

1. Trip completion race condition:
   ```typescript
   test('Trip completion with pending mobile sync', async ({ page, context }) => {
       // Simulate mobile with pending tickets
       await setTripSyncStatus(42, 'pending')
       
       // Web completes trip
       await page.goto('/office/trips/42')
       await page.click('[role="button"]:has-text("Clôturer")')
       
       // Settlement should be blocked
       await page.waitForTimeout(2000)
       const settlementError = await page.locator('[role="alert"]').textContent()
       expect(settlementError).toContain('being created')
       
       // Simulate mobile completing sync
       await setTripSyncStatus(42, 'synced')
       
       // Settlement should now appear
       await page.reload()
       await expect(page.locator('[role="heading"]:has-text("Reglement")')).toBeVisible()
   })
   ```

2. Cargo ticket permission check:
   ```typescript
   test('Cargo staff cannot create cargo tickets', async ({ page }) => {
       // Login as cargo staff
       await loginAs('cargo_staff@example.com')
       
       // Navigate to cargo creation
       await page.goto('/office/cargo/new')
       
       // Try to create ticket
       await page.fill('[name="sender"]', 'Ali')
       await page.fill('[name="receiver"]', 'Bob')
       await page.click('[role="button"]:has-text("Create")')
       
       // Verify permission denied
       const error = await page.locator('[role="alert"]').textContent()
       expect(error).toContain('Permission denied')
       
       // Verify no queue item created
       const queueCount = await getQueueItemCount('cargo_ticket')
       expect(queueCount).toBe(0)
   })
   ```

**Effort:** 3–4 hours

---

## DOCUMENTATION & TRAINING

### Task D1: Update Developer Docs (P2)

**Problem:**
- Existing developers need to understand new error handling, sync queue, etc.

**Solution:**

1. Create `SYNC_ARCHITECTURE.md`:
   ```markdown
   # Sync Architecture

   ## Data Flow

   ### Mobile (Android)
   1. Conductor creates/completes trip locally (Room DB)
   2. Enqueue to sync queue with type `trip_status_backend`
   3. Worker classifies errors: terminal vs transient
   4. On success: mark "synced"
   5. On terminal error: mark "failed", don't retry
   6. On transient: exponential backoff (max 5 retries)

   ### Web (React)
   1. Admin creates trip/ticket via API
   2. API succeeds → enqueue to IndexedDB
   3. Worker validates permission (cargo tickets)
   4. On invalid: mark "terminal", don't write Firestore
   5. On valid: write to Firestore + backend audit log

   ## Error Classification

   - Terminal: 400, 401, 403, 404 (don't retry)
   - Transient: 429, 5xx (retry with backoff)
   - Network: timeout, connection refused (retry)

   ## Testing

   - Unit tests for ErrorClassifier
   - E2E tests for trip completion → settlement
   - E2E tests for permission denials
   ```

2. Create `TROUBLESHOOTING.md`:
   ```markdown
   # Troubleshooting Sync Issues

   ## Stuck Sync Items

   Mobile: Check `sync_audit` table for `STUCK_RESET` entries
   Web: Check backend `/api/sync/audit-log/` for failed items

   ## Settlement 404 Errors

   - Check trip.sync_status on backend
   - If "pending": mobile still syncing, try again in 5s
   - If "failed": mobile sync failed, check mobile logs

   ## Cargo Ticket Not Appearing on Mobile

   - Check web permission validation passed
   - Check backend audit log for permission errors (403)
   - Check Firestore document created
   ```

**Effort:** 2–3 hours

---

## SUMMARY TABLE

| Task ID | Component | Title | Priority | Effort | Completed? |
|---------|-----------|-------|----------|--------|-----------|
| M1 | Mobile | Fix Trip Status Fallback | P0 | 3–4h | ⬜ |
| M2 | Mobile | Add Trip Status Backend Queue | P0 | 4–5h | ⬜ |
| M3 | Mobile | Route to Backend API in Worker | P0 | 3–4h | ⬜ |
| M4 | Mobile | Timestamp-Aware Merge for Listeners | P0 | 3–4h | ⬜ |
| M5 | Mobile | Enforce Retryable Error Classification | P0 | 2–3h | ⬜ |
| M6 | Mobile | Trigger Immediate Sync for Trip Status | P0 | 2–3h | ⬜ |
| M7 | Mobile | Log Stuck Syncing Items | P0 | 2–3h | ⬜ |
| M8 | Mobile | Implement Idempotency for Trip Status | P1 | 2–3h | ⬜ |
| M9 | Mobile | Add Timestamp to Trip Mirror | P1 | 1–2h | ⬜ |
| M10 | Mobile | Add Sync Status UI Indicator | P2 | 2–3h | ⬜ |
| M11 | Mobile | Add Queue Retry Details UI | P2 | 2–3h | ⬜ |
| W1 | Web | Fix Mirror Status Override Logic | P0 | 1–2h | ⬜ |
| W2 | Web | Add Immediate Refetch | P0 | 1–2h | ⬜ |
| W3 | Web | Add Settlement Query Delay | P0 | 1–2h | ⬜ |
| W4 | Web | Timestamp-Aware Merge | P0 | 2–3h | ⬜ |
| W5 | Web | Validate Cargo Ticket Backend | P1 | 3–4h | ⬜ |
| W6 | Web | Exponential Backoff for Errors | P1 | 2–3h | ⬜ |
| W7 | Web | Backend Audit Log Endpoint | P1 | 3–4h | ⬜ |
| W8 | Web | Sync Status Endpoint | P1 | 2–3h | ⬜ |
| W9 | Web | Sync Status Indicators | P2 | 1–2h | ⬜ |
| W10 | Web | Debounce Listeners | P2 | 1–2h | ⬜ |
| W11 | Web | Client-Side Role Validation | P2 | 1–2h | ⬜ |
| W12 | Web | Increase Stale Time | P2 | 1–2h | ⬜ |
| W13 | Web | Settlement Creation Delay | P2 | 1h | ⬜ |
| B1 | Backend | Add sync_status Field | P1 | 2–3h | ⬜ |
| B2 | Backend | Audit Log Endpoint | P1 | 3–4h | ⬜ |
| CS1 | Cross-System | E2E Trip Completion | P0 | 4–5h | ⬜ |
| T1 | Testing | Unit Tests | P2 | 1–2h | ⬜ |
| T2 | Testing | E2E Tests | P2 | 3–4h | ⬜ |
| D1 | Docs | Developer Documentation | P2 | 2–3h | ⬜ |

**Total Effort:** 71–91 hours (~2–3 weeks, 2 devs)

---

**TASK.MD END**

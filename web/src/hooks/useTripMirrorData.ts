import { useEffect, useState } from 'react'
import { collection, doc, documentId, getDocs, limit, onSnapshot, orderBy, query, where, type DocumentData, type QueryDocumentSnapshot } from 'firebase/firestore'
import type { CargoTicket, PassengerTicket } from '../types/ticket'
import type { TripExpense } from '../api/expenses'
import { getTripExpenses } from '../api/expenses'
import { getTripCargoTickets } from '../api/cargo'
import { getTripPassengerTickets } from '../api/tickets'
import type { TripStatus } from '../types/trip'
import { ensureFirebaseSession } from '../firebase/auth'
import { getFirebaseFirestore } from '../firebase/firestore'

interface MirrorHookState<T> {
    data: T[]
    isLoading: boolean
    error: string | null
}

interface MirrorCollectionOptions {
    enableRealtime?: boolean
}

interface CargoMirrorFeedOptions {
    enabled?: boolean
    officeId?: number | null
    cacheTtlMs?: number
    limitCount?: number
}

interface CargoMirrorFeedState {
    data: CargoTicket[]
    isLoading: boolean
    error: string | null
}

interface TripStatusMirrorOptions {
    enabled?: boolean
}

interface TripStatusMirrorMapOptions {
    enabled?: boolean
    enableRealtime?: boolean
    cacheTtlMs?: number
}

interface TripStatusMirrorState {
    status: TripStatus | null
    arrivalDatetime: string | null
    sourceUpdatedAt: string | null
    isLoading: boolean
    error: string | null
}

interface TripStatusMirrorMapState {
    statuses: Record<number, { status: TripStatus | null; arrivalDatetime: string | null; sourceUpdatedAt: string | null }>
    isLoading: boolean
    error: string | null
}

type TripMirrorStatusEntry = {
    status: TripStatus | null
    arrivalDatetime: string | null
    sourceUpdatedAt: string | null
}

type CachedTripMirrorStatusEntry = TripMirrorStatusEntry & { cachedAt: number }

const TRIP_STATUS_CACHE_KEY = 'souigat_trip_status_cache_v1'
const TRIP_STATUS_CACHE_TTL_MS_DEFAULT = 60_000
const CARGO_FEED_CACHE_PREFIX = 'souigat_cargo_feed_cache_v1:'
const CARGO_FEED_CACHE_TTL_MS_DEFAULT = 120_000
const TRIP_COLLECTION_CACHE_PREFIX = 'souigat_trip_collection_cache_v2:'
const TRIP_COLLECTION_CACHE_TTL_MS_DEFAULT = 60 * 1000
const tripStatusMemoryCache = new Map<number, CachedTripMirrorStatusEntry>()

type CachedCargoFeed = {
    cachedAt: number
    data: CargoTicket[]
}

type CachedTripCollection<T> = {
    cachedAt: number
    data: T[]
}

function readPersistentTripStatusCache(): Record<number, CachedTripMirrorStatusEntry> {
    try {
        const raw = localStorage.getItem(TRIP_STATUS_CACHE_KEY)
        if (!raw) {
            return {}
        }
        const parsed = JSON.parse(raw) as Record<string, CachedTripMirrorStatusEntry>
        const out: Record<number, CachedTripMirrorStatusEntry> = {}
        Object.entries(parsed).forEach(([key, value]) => {
            const id = Number(key)
            if (!Number.isFinite(id) || !value || typeof value.cachedAt !== 'number') {
                return
            }
            out[id] = value
        })
        return out
    } catch {
        return {}
    }
}

function writePersistentTripStatusCache(cache: Record<number, CachedTripMirrorStatusEntry>): void {
    try {
        localStorage.setItem(TRIP_STATUS_CACHE_KEY, JSON.stringify(cache))
    } catch {
        // Ignore storage quota/access errors; memory cache remains functional.
    }
}

function getCachedTripStatus(
    tripId: number,
    ttlMs: number,
    persistentCache: Record<number, CachedTripMirrorStatusEntry>,
): TripMirrorStatusEntry | null {
    const now = Date.now()
    const fromMemory = tripStatusMemoryCache.get(tripId)
    if (fromMemory && (now - fromMemory.cachedAt) <= ttlMs) {
        return {
            status: fromMemory.status,
            arrivalDatetime: fromMemory.arrivalDatetime,
            sourceUpdatedAt: fromMemory.sourceUpdatedAt,
        }
    }

    const fromPersistent = persistentCache[tripId]
    if (fromPersistent && (now - fromPersistent.cachedAt) <= ttlMs) {
        tripStatusMemoryCache.set(tripId, fromPersistent)
        return {
            status: fromPersistent.status,
            arrivalDatetime: fromPersistent.arrivalDatetime,
            sourceUpdatedAt: fromPersistent.sourceUpdatedAt,
        }
    }

    return null
}

function cacheTripStatusEntry(
    tripId: number,
    entry: TripMirrorStatusEntry,
    persistentCache: Record<number, CachedTripMirrorStatusEntry>,
): void {
    const cachedEntry: CachedTripMirrorStatusEntry = {
        ...entry,
        cachedAt: Date.now(),
    }
    tripStatusMemoryCache.set(tripId, cachedEntry)
    persistentCache[tripId] = cachedEntry
}

function readCargoFeedCache(cacheKey: string): CachedCargoFeed | null {
    try {
        const raw = localStorage.getItem(`${CARGO_FEED_CACHE_PREFIX}${cacheKey}`)
        if (!raw) {
            return null
        }
        const parsed = JSON.parse(raw) as CachedCargoFeed
        if (!parsed || typeof parsed.cachedAt !== 'number' || !Array.isArray(parsed.data)) {
            return null
        }
        return parsed
    } catch {
        return null
    }
}

function writeCargoFeedCache(cacheKey: string, data: CargoTicket[]): void {
    try {
        const payload: CachedCargoFeed = {
            cachedAt: Date.now(),
            data,
        }
        localStorage.setItem(`${CARGO_FEED_CACHE_PREFIX}${cacheKey}`, JSON.stringify(payload))
    } catch {
        // Ignore cache storage failures.
    }
}

function readTripCollectionCache<T>(cacheKey: string): CachedTripCollection<T> | null {
    try {
        const raw = localStorage.getItem(`${TRIP_COLLECTION_CACHE_PREFIX}${cacheKey}`)
        if (!raw) {
            return null
        }
        const parsed = JSON.parse(raw) as CachedTripCollection<T>
        if (!parsed || typeof parsed.cachedAt !== 'number' || !Array.isArray(parsed.data)) {
            return null
        }
        return parsed
    } catch {
        return null
    }
}

function writeTripCollectionCache<T>(cacheKey: string, data: T[]): void {
    try {
        const payload: CachedTripCollection<T> = {
            cachedAt: Date.now(),
            data,
        }
        localStorage.setItem(`${TRIP_COLLECTION_CACHE_PREFIX}${cacheKey}`, JSON.stringify(payload))
    } catch {
        // Ignore storage quota issues.
    }
}

function parseNumber(value: unknown, fallback = 0): number {
    if (typeof value === 'number' && Number.isFinite(value)) {
        return value
    }
    if (typeof value === 'string') {
        const parsed = Number(value)
        if (Number.isFinite(parsed)) {
            return parsed
        }
    }
    return fallback
}

function parseString(value: unknown, fallback = ''): string {
    return typeof value === 'string' ? value : fallback
}

function chunkIds(ids: number[], size: number): number[][] {
    if (ids.length === 0) {
        return []
    }

    const chunks: number[][] = []
    for (let index = 0; index < ids.length; index += size) {
        chunks.push(ids.slice(index, index + size))
    }
    return chunks
}

function toPassengerTicket(docData: DocumentData): PassengerTicket | null {
    const id = parseNumber(docData.id, NaN)
    const tripId = parseNumber(docData.trip_id, NaN)
    if (!Number.isFinite(id) || !Number.isFinite(tripId)) {
        return null
    }

    return {
        id,
        trip: tripId,
        ticket_number: parseString(docData.ticket_number, `PT-${tripId}-${id}`),
        passenger_name: parseString(docData.passenger_name, 'Passager'),
        price: parseNumber(docData.price),
        currency: parseString(docData.currency, 'DZD'),
        payment_source: (parseString(docData.payment_source, 'cash') === 'prepaid' ? 'prepaid' : 'cash'),
        seat_number: parseString(docData.seat_number, '') || null,
        boarding_point: parseString(docData.boarding_point, '') || null,
        alighting_point: parseString(docData.alighting_point, '') || null,
        status: (parseString(docData.status, 'active') as PassengerTicket['status']),
        created_by: parseNumber(docData.created_by_id),
        created_by_name: parseString(docData.created_by_name),
        version: 1,
        synced_at: parseString(docData.source_updated_at) || null,
        created_at: parseString(docData.source_created_at, new Date().toISOString()),
        updated_at: parseString(docData.source_updated_at, new Date().toISOString()),
    }
}

function toCargoTicket(docData: DocumentData): CargoTicket | null {
    const id = parseNumber(docData.id, NaN)
    const tripId = parseNumber(docData.trip_id, NaN)
    if (!Number.isFinite(id) || !Number.isFinite(tripId)) {
        return null
    }

    return {
        id,
        trip: tripId,
        trip_destination_office_id: parseNumber(docData.destination_office_id),
        ticket_number: parseString(docData.ticket_number, `CT-${tripId}-${id}`),
        sender_name: parseString(docData.sender_name),
        sender_phone: parseString(docData.sender_phone) || null,
        receiver_name: parseString(docData.receiver_name),
        receiver_phone: parseString(docData.receiver_phone) || null,
        cargo_tier: (parseString(docData.cargo_tier, 'small') as CargoTicket['cargo_tier']),
        description: parseString(docData.description) || null,
        price: parseNumber(docData.price),
        currency: parseString(docData.currency, 'DZD'),
        payment_source: (parseString(docData.payment_source, 'prepaid') as CargoTicket['payment_source']),
        status: (parseString(docData.status, 'created') as CargoTicket['status']),
        status_override_reason: parseString(docData.status_override_reason) || null,
        status_override_by: parseNumber(docData.status_override_by_id) || null,
        delivered_at: parseString(docData.delivered_at) || null,
        delivered_by: parseNumber(docData.delivered_by_id) || null,
        created_by: parseNumber(docData.created_by_id),
        created_by_name: parseString(docData.created_by_name),
        version: 1,
        synced_at: parseString(docData.source_updated_at) || null,
        created_at: parseString(docData.source_created_at, new Date().toISOString()),
        updated_at: parseString(docData.source_updated_at, new Date().toISOString()),
    }
}

function toTripExpense(docData: DocumentData): TripExpense | null {
    const id = parseNumber(docData.id, NaN)
    const tripId = parseNumber(docData.trip_id, NaN)
    if (!Number.isFinite(id) || !Number.isFinite(tripId)) {
        return null
    }

    return {
        id,
        trip: tripId,
        description: parseString(docData.description),
        amount: parseNumber(docData.amount),
        currency: parseString(docData.currency, 'DZD'),
        category: parseString(docData.category),
        created_by: parseNumber(docData.created_by_id),
        created_by_name: parseString(docData.created_by_name),
        created_at: parseString(docData.source_created_at, new Date().toISOString()),
        updated_at: parseString(docData.source_updated_at, new Date().toISOString()),
    }
}

function useTripMirrorCollection<T>(
    tripId: number,
    collectionName: string,
    mapper: (docData: DocumentData) => T | null,
    fallbackFetch: (tripId: number) => Promise<T[]>,
    options?: MirrorCollectionOptions,
): MirrorHookState<T> {
    const [state, setState] = useState<MirrorHookState<T>>({
        data: [],
        isLoading: true,
        error: null,
    })

    useEffect(() => {
        const enableRealtime = options?.enableRealtime ?? false
        const cacheKey = `${collectionName}:trip_${tripId}`
        const cached = readTripCollectionCache<T>(cacheKey)
        const hasFreshCached = Boolean(
            cached && (Date.now() - cached.cachedAt) <= TRIP_COLLECTION_CACHE_TTL_MS_DEFAULT,
        )
        const cachedData = hasFreshCached ? (cached?.data ?? []) : []

        if (!tripId || !Number.isFinite(tripId)) {
            setState({ data: [], isLoading: false, error: null })
            return
        }

        if (hasFreshCached) {
            setState({ data: cachedData, isLoading: false, error: null })
        }

        let unsubscribe: (() => void) | null = null
        let active = true
        const byDocId = new Map<string, T>()

        const toOrderedArray = (docs: QueryDocumentSnapshot<DocumentData>[]): T[] => {
            const ordered: T[] = []
            docs.forEach((entry) => {
                const mapped = byDocId.get(entry.id)
                if (mapped !== undefined) {
                    ordered.push(mapped)
                }
            })
            return ordered
        }

        const loadBackendFallback = async () => {
            try {
                const fallbackData = await fallbackFetch(tripId)
                if (active) {
                    writeTripCollectionCache(cacheKey, fallbackData)
                    setState({ data: fallbackData, isLoading: false, error: null })
                }
            } catch {
                if (active) {
                    setState({
                        data: cachedData,
                        isLoading: false,
                        error: cachedData.length ? null : 'Erreur de chargement des donnees.',
                    })
                }
            }
        }

        const start = async () => {
            setState((prev) => ({ ...prev, isLoading: true, error: null }))

            if (!enableRealtime) {
                await loadBackendFallback()
                return
            }

            const sessionReady = await ensureFirebaseSession()
            const firestore = getFirebaseFirestore()

            if (!active) {
                return
            }

            if (!sessionReady || !firestore) {
                if (hasFreshCached) {
                    setState({
                        data: cachedData,
                        isLoading: false,
                        error: null,
                    })
                    return
                }

                await loadBackendFallback()
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
                    if (!active) {
                        return
                    }

                    snapshot.docChanges().forEach((change) => {
                        if (change.type === 'removed') {
                            byDocId.delete(change.doc.id)
                            return
                        }

                        const mapped = mapper(change.doc.data())
                        if (mapped === null) {
                            byDocId.delete(change.doc.id)
                            return
                        }
                        byDocId.set(change.doc.id, mapped)
                    })

                    const ordered = toOrderedArray(snapshot.docs)
                    setState({
                        data: ordered,
                        isLoading: false,
                        error: null,
                    })
                    writeTripCollectionCache(cacheKey, ordered)
                },
                (error) => {
                    console.warn(`[FIREBASE] ${collectionName} listener failed.`, error)
                    void (async () => {
                        await loadBackendFallback()
                    })()
                },
            )
        }

        void start()

        return () => {
            active = false
            if (unsubscribe) {
                unsubscribe()
            }
        }
    }, [tripId, collectionName, mapper, fallbackFetch, options?.enableRealtime])

    return state
}

export function useCargoMirrorFeed(options?: CargoMirrorFeedOptions): CargoMirrorFeedState {
    const enabled = options?.enabled ?? true
    const officeId = options?.officeId ?? null
    const cacheTtlMs = options?.cacheTtlMs ?? CARGO_FEED_CACHE_TTL_MS_DEFAULT
    const limitCount = options?.limitCount ?? 1000
    const cacheKey = officeId && Number.isFinite(officeId)
        ? `office_${officeId}`
        : 'admin_all'

    const initialCache = readCargoFeedCache(cacheKey)
    const isFreshCache = Boolean(initialCache && (Date.now() - initialCache.cachedAt) <= cacheTtlMs)
    const [state, setState] = useState<CargoMirrorFeedState>({
        data: isFreshCache ? (initialCache?.data ?? []) : [],
        isLoading: enabled,
        error: null,
    })

    useEffect(() => {
        if (!enabled) {
            setState({ data: [], isLoading: false, error: null })
            return
        }

        const cached = readCargoFeedCache(cacheKey)
        const hasFreshCached = Boolean(cached && (Date.now() - cached.cachedAt) <= cacheTtlMs)
        if (hasFreshCached) {
            setState({ data: cached?.data ?? [], isLoading: false, error: null })
        } else {
            setState((prev) => ({ ...prev, isLoading: true, error: null }))
        }

        let active = true
        let unsubscribe: (() => void) | null = null
        const byDocId = new Map<string, CargoTicket>()

        const emitFromSnapshot = (docs: QueryDocumentSnapshot<DocumentData>[]) => {
            const ordered: CargoTicket[] = []
            docs.forEach((entry) => {
                const mapped = byDocId.get(entry.id)
                if (mapped) {
                    ordered.push(mapped)
                }
            })
            writeCargoFeedCache(cacheKey, ordered)
            if (active) {
                setState({ data: ordered, isLoading: false, error: null })
            }
        }

        const start = async () => {
            const sessionReady = await ensureFirebaseSession()
            const firestore = getFirebaseFirestore()

            if (!active) {
                return
            }

            if (!sessionReady || !firestore) {
                if (!hasFreshCached) {
                    setState({ data: [], isLoading: false, error: 'Firebase non disponible.' })
                }
                return
            }

            const feedQuery = officeId && Number.isFinite(officeId)
                ? query(
                    collection(firestore, 'cargo_ticket_mirror_v1'),
                    where('office_scope_ids', 'array-contains', officeId),
                    where('is_deleted', '==', false),
                    orderBy('source_created_at', 'desc'),
                    limit(limitCount),
                )
                : query(
                    collection(firestore, 'cargo_ticket_mirror_v1'),
                    orderBy('source_created_at', 'desc'),
                    limit(limitCount),
                )

            unsubscribe = onSnapshot(
                feedQuery,
                (snapshot) => {
                    if (!active) {
                        return
                    }

                    snapshot.docChanges().forEach((change) => {
                        if (change.type === 'removed') {
                            byDocId.delete(change.doc.id)
                            return
                        }

                        const raw = change.doc.data()
                        if (raw?.is_deleted === true) {
                            byDocId.delete(change.doc.id)
                            return
                        }

                        const mapped = toCargoTicket(raw)
                        if (mapped === null) {
                            byDocId.delete(change.doc.id)
                            return
                        }
                        byDocId.set(change.doc.id, mapped)
                    })

                    emitFromSnapshot(snapshot.docs)
                },
                (error) => {
                    console.warn('[FIREBASE] cargo_ticket_mirror_v1 listener failed.', error)
                    if (active && !hasFreshCached) {
                        setState({ data: [], isLoading: false, error: 'Erreur Firebase temps réel.' })
                    }
                },
            )
        }

        void start()

        return () => {
            active = false
            if (unsubscribe) {
                unsubscribe()
            }
        }
    }, [enabled, officeId, cacheKey, cacheTtlMs, limitCount])

    return state
}

export function useTripPassengerMirror(
    tripId: number,
    options?: MirrorCollectionOptions,
): MirrorHookState<PassengerTicket> {
    return useTripMirrorCollection(
        tripId,
        'passenger_ticket_mirror_v1',
        toPassengerTicket,
        getTripPassengerTickets,
        options,
    )
}

export function useTripCargoMirror(
    tripId: number,
    options?: MirrorCollectionOptions,
): MirrorHookState<CargoTicket> {
    return useTripMirrorCollection(
        tripId,
        'cargo_ticket_mirror_v1',
        toCargoTicket,
        getTripCargoTickets,
        options,
    )
}

export function useTripExpenseMirror(
    tripId: number,
    options?: MirrorCollectionOptions,
): MirrorHookState<TripExpense> {
    return useTripMirrorCollection(
        tripId,
        'trip_expense_mirror_v1',
        toTripExpense,
        getTripExpenses,
        options,
    )
}

export function useTripStatusMirror(
    tripId: number,
    options?: TripStatusMirrorOptions,
): TripStatusMirrorState {
    const [state, setState] = useState<TripStatusMirrorState>({
        status: null,
        arrivalDatetime: null,
        sourceUpdatedAt: null,
        isLoading: true,
        error: null,
    })

    useEffect(() => {
        const enabled = options?.enabled ?? true

        if (!enabled) {
            setState({ status: null, arrivalDatetime: null, sourceUpdatedAt: null, isLoading: false, error: null })
            return
        }

        if (!tripId || !Number.isFinite(tripId)) {
            setState({ status: null, arrivalDatetime: null, sourceUpdatedAt: null, isLoading: false, error: null })
            return
        }

        let unsubscribe: (() => void) | null = null
        let active = true

        const start = async () => {
            setState({ status: null, arrivalDatetime: null, sourceUpdatedAt: null, isLoading: true, error: null })
            const sessionReady = await ensureFirebaseSession()
            const firestore = getFirebaseFirestore()

            if (!active) {
                return
            }

            if (!sessionReady || !firestore) {
                setState({ status: null, arrivalDatetime: null, sourceUpdatedAt: null, isLoading: false, error: 'Firebase non disponible.' })
                return
            }

            const tripRef = doc(firestore, 'trip_mirror_v1', String(tripId))
            unsubscribe = onSnapshot(
                tripRef,
                (snapshot) => {
                    if (!active) {
                        return
                    }

                    const data = snapshot.data()
                    if (!snapshot.exists() || !data || data.is_deleted === true) {
                        setState({ status: null, arrivalDatetime: null, sourceUpdatedAt: null, isLoading: false, error: null })
                        return
                    }

                    const status = parseString(data.status, '') as TripStatus | ''
                    const safeStatus = ['scheduled', 'in_progress', 'completed', 'cancelled'].includes(status)
                        ? status as TripStatus
                        : null

                    setState({
                        status: safeStatus,
                        arrivalDatetime: parseString(data.arrival_datetime) || null,
                        sourceUpdatedAt: parseString(data.source_updated_at) || null,
                        isLoading: false,
                        error: null,
                    })
                },
                (error) => {
                    console.warn('[FIREBASE] trip_mirror_v1 status listener failed.', error)
                    if (active) {
                        setState({ status: null, arrivalDatetime: null, sourceUpdatedAt: null, isLoading: false, error: 'Erreur Firebase temps réel.' })
                    }
                },
            )
        }

        void start()

        return () => {
            active = false
            if (unsubscribe) {
                unsubscribe()
            }
        }
    }, [tripId, options?.enabled])

    return state
}

export function useTripStatusMirrorMap(
    tripIds: number[],
    options?: TripStatusMirrorMapOptions,
): TripStatusMirrorMapState {
    const [state, setState] = useState<TripStatusMirrorMapState>({
        statuses: {},
        isLoading: true,
        error: null,
    })

    useEffect(() => {
        const enabled = options?.enabled ?? true
        const enableRealtime = options?.enableRealtime ?? true
        const cacheTtlMs = options?.cacheTtlMs ?? TRIP_STATUS_CACHE_TTL_MS_DEFAULT

        if (!enabled) {
            setState({ statuses: {}, isLoading: false, error: null })
            return
        }

        const uniqueIds = Array.from(
            new Set(tripIds.filter((tripId) => Number.isFinite(tripId) && tripId > 0)),
        )

        if (uniqueIds.length === 0) {
            setState({ statuses: {}, isLoading: false, error: null })
            return
        }

        const persistentCache = readPersistentTripStatusCache()
        const cachedStatuses: Record<number, TripMirrorStatusEntry> = {}
        const uncachedTripIds: number[] = []

        uniqueIds.forEach((tripId) => {
            const cachedEntry = getCachedTripStatus(tripId, cacheTtlMs, persistentCache)
            if (cachedEntry) {
                cachedStatuses[tripId] = cachedEntry
            } else {
                uncachedTripIds.push(tripId)
            }
        })

        if (!enableRealtime && uncachedTripIds.length === 0) {
            setState({ statuses: cachedStatuses, isLoading: false, error: null })
            return
        }

        let active = true
        const listeners: Array<() => void> = []
        const chunkStates = new Map<number, Record<number, { status: TripStatus | null; arrivalDatetime: string | null; sourceUpdatedAt: string | null }>>()
        const chunks = chunkIds(enableRealtime ? uniqueIds : uncachedTripIds, 10)

        const emitMergedState = () => {
            if (!active) {
                return
            }

            const merged: Record<number, { status: TripStatus | null; arrivalDatetime: string | null; sourceUpdatedAt: string | null }> = {
                ...cachedStatuses,
            }
            chunkStates.forEach((chunkState) => {
                Object.assign(merged, chunkState)
            })

            setState({
                statuses: merged,
                isLoading: false,
                error: null,
            })
        }

        const start = async () => {
            setState({
                statuses: cachedStatuses,
                isLoading: uncachedTripIds.length > 0 || enableRealtime,
                error: null,
            })

            const sessionReady = await ensureFirebaseSession()
            const firestore = getFirebaseFirestore()

            if (!active) {
                return
            }

            if (!sessionReady || !firestore) {
                setState({ statuses: cachedStatuses, isLoading: false, error: 'Firebase non disponible.' })
                return
            }

            if (!enableRealtime) {
                await Promise.all(chunks.map(async (chunk, chunkIndex) => {
                    const chunkQuery = query(
                        collection(firestore, 'trip_mirror_v1'),
                        where(documentId(), 'in', chunk.map((tripId) => String(tripId))),
                    )
                    const snapshot = await getDocs(chunkQuery)
                    if (!active) {
                        return
                    }

                    const currentChunkState: Record<number, TripMirrorStatusEntry> = {}
                    snapshot.docs.forEach((document) => {
                        const data = document.data()
                        if (!data || data.is_deleted === true) {
                            return
                        }

                        const id = Number(document.id)
                        if (!Number.isFinite(id)) {
                            return
                        }

                        const parsedStatus = parseString(data.status, '') as TripStatus | ''
                        const safeStatus = ['scheduled', 'in_progress', 'completed', 'cancelled'].includes(parsedStatus)
                            ? parsedStatus as TripStatus
                            : null

                        const entry: TripMirrorStatusEntry = {
                            status: safeStatus,
                            arrivalDatetime: parseString(data.arrival_datetime) || null,
                            sourceUpdatedAt: parseString(data.source_updated_at) || null,
                        }

                        currentChunkState[id] = entry
                        cacheTripStatusEntry(id, entry, persistentCache)
                    })

                    chunkStates.set(chunkIndex, currentChunkState)
                }))

                writePersistentTripStatusCache(persistentCache)
                emitMergedState()
                return
            }

            chunks.forEach((chunk, chunkIndex) => {
                const chunkQuery = query(
                    collection(firestore, 'trip_mirror_v1'),
                    where(documentId(), 'in', chunk.map((tripId) => String(tripId))),
                )

                const unsubscribe = onSnapshot(
                    chunkQuery,
                    (snapshot) => {
                        if (!active) {
                            return
                        }

                        const currentChunkState: Record<number, { status: TripStatus | null; arrivalDatetime: string | null; sourceUpdatedAt: string | null }> = {}
                        snapshot.docs.forEach((document) => {
                            const data = document.data()
                            if (!data || data.is_deleted === true) {
                                return
                            }

                            const id = Number(document.id)
                            if (!Number.isFinite(id)) {
                                return
                            }

                            const parsedStatus = parseString(data.status, '') as TripStatus | ''
                            const safeStatus = ['scheduled', 'in_progress', 'completed', 'cancelled'].includes(parsedStatus)
                                ? parsedStatus as TripStatus
                                : null

                            currentChunkState[id] = {
                                status: safeStatus,
                                arrivalDatetime: parseString(data.arrival_datetime) || null,
                                sourceUpdatedAt: parseString(data.source_updated_at) || null,
                            }
                            cacheTripStatusEntry(id, currentChunkState[id], persistentCache)
                        })

                        writePersistentTripStatusCache(persistentCache)
                        chunkStates.set(chunkIndex, currentChunkState)
                        emitMergedState()
                    },
                    (error) => {
                        console.warn('[FIREBASE] trip_mirror_v1 list status listener failed.', error)
                        if (active) {
                            setState({ statuses: cachedStatuses, isLoading: false, error: 'Erreur Firebase temps réel.' })
                        }
                    },
                )

                listeners.push(unsubscribe)
            })
        }

        void start()

        return () => {
            active = false
            listeners.forEach((unsubscribe) => unsubscribe())
        }
    }, [tripIds, options?.enabled, options?.enableRealtime, options?.cacheTtlMs])

    return state
}

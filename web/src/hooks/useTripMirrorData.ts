import { useEffect, useState } from 'react'
import { collection, doc, documentId, limit, onSnapshot, orderBy, query, where, type DocumentData } from 'firebase/firestore'
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

interface TripStatusMirrorOptions {
    enabled?: boolean
}

interface TripStatusMirrorMapOptions {
    enabled?: boolean
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

        if (!tripId || !Number.isFinite(tripId)) {
            setState({ data: [], isLoading: false, error: null })
            return
        }

        let unsubscribe: (() => void) | null = null
        let active = true

        const loadBackendFallback = async () => {
            try {
                const fallbackData = await fallbackFetch(tripId)
                if (active) {
                    setState({ data: fallbackData, isLoading: false, error: null })
                }
            } catch {
                if (active) {
                    setState({ data: [], isLoading: false, error: 'Erreur de chargement des donnees.' })
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

                    const mapped = snapshot.docs
                        .map((document) => mapper(document.data()))
                        .filter((item): item is T => item !== null)

                    setState({ data: mapped, isLoading: false, error: null })
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

        let active = true
        const listeners: Array<() => void> = []
        const chunkStates = new Map<number, Record<number, { status: TripStatus | null; arrivalDatetime: string | null; sourceUpdatedAt: string | null }>>()
        const chunks = chunkIds(uniqueIds, 10)

        const emitMergedState = () => {
            if (!active) {
                return
            }

            const merged: Record<number, { status: TripStatus | null; arrivalDatetime: string | null; sourceUpdatedAt: string | null }> = {}
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
            setState({ statuses: {}, isLoading: true, error: null })

            const sessionReady = await ensureFirebaseSession()
            const firestore = getFirebaseFirestore()

            if (!active) {
                return
            }

            if (!sessionReady || !firestore) {
                setState({ statuses: {}, isLoading: false, error: 'Firebase non disponible.' })
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
                        })

                        chunkStates.set(chunkIndex, currentChunkState)
                        emitMergedState()
                    },
                    (error) => {
                        console.warn('[FIREBASE] trip_mirror_v1 list status listener failed.', error)
                        if (active) {
                            setState({ statuses: {}, isLoading: false, error: 'Erreur Firebase temps réel.' })
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
    }, [tripIds, options?.enabled])

    return state
}

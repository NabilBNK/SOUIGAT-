import { useEffect, useState } from 'react'
import { collection, limit, onSnapshot, orderBy, query, where, type DocumentData } from 'firebase/firestore'
import type { CargoTicket, PassengerTicket } from '../types/ticket'
import type { TripExpense } from '../api/expenses'
import { ensureFirebaseSession } from '../firebase/auth'
import { getFirebaseFirestore } from '../firebase/firestore'

interface MirrorHookState<T> {
    data: T[]
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

        const start = async () => {
            setState((prev) => ({ ...prev, isLoading: true, error: null }))
            const sessionReady = await ensureFirebaseSession()
            const firestore = getFirebaseFirestore()

            if (!active) {
                return
            }

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
                    if (active) {
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
    }, [tripId, collectionName, mapper])

    return state
}

export function useTripPassengerMirror(tripId: number): MirrorHookState<PassengerTicket> {
    return useTripMirrorCollection(tripId, 'passenger_ticket_mirror_v1', toPassengerTicket)
}

export function useTripCargoMirror(tripId: number): MirrorHookState<CargoTicket> {
    return useTripMirrorCollection(tripId, 'cargo_ticket_mirror_v1', toCargoTicket)
}

export function useTripExpenseMirror(tripId: number): MirrorHookState<TripExpense> {
    return useTripMirrorCollection(tripId, 'trip_expense_mirror_v1', toTripExpense)
}

import client from './client'
import type { Trip, TripActionResponse, TripCreate, TripFilters, TripStatus } from '../types/trip'
import type { Office, Bus } from '../types/admin'
import type { User } from '../types/auth'

interface PaginatedResponse<T> {
    count: number
    next: string | null
    previous: string | null
    results: T[]
}

export interface TripReferenceData {
    offices: Office[]
    buses: Bus[]
    conductors: User[]
}

export async function getTrips(filters?: TripFilters): Promise<PaginatedResponse<Trip>> {
    const response = await client.get<PaginatedResponse<Trip>>('/trips/', { params: filters })
    return response.data
}

export async function getTripsForCargoCreation(): Promise<Trip[]> {
    const statuses: TripStatus[] = ['scheduled', 'in_progress']
    const dedup = new Map<number, Trip>()

    for (const status of statuses) {
        let page = 1

        while (true) {
            const response = await client.get<PaginatedResponse<Trip>>('/trips/', {
                params: {
                    status,
                    page,
                    page_size: 100,
                },
            })

            response.data.results.forEach((trip) => {
                dedup.set(trip.id, trip)
            })

            if (!response.data.next || response.data.results.length === 0) {
                break
            }
            page += 1
        }
    }

    return Array.from(dedup.values()).sort((a, b) => b.id - a.id)
}

export async function getTrip(id: number): Promise<Trip> {
    const response = await client.get<Trip>(`/trips/${id}/`)
    return response.data
}

export async function createTrip(data: TripCreate): Promise<Trip> {
    const response = await client.post<Trip>('/trips/', data)
    return response.data
}

export async function getTripReferenceData(currentOfficeId?: number): Promise<TripReferenceData> {
    const response = await client.get<TripReferenceData>('/trips/reference-data/', {
        params: currentOfficeId ? { current_office_id: currentOfficeId } : undefined,
    })
    return response.data
}

export async function startTrip(id: number): Promise<TripActionResponse> {
    const response = await client.post<TripActionResponse>(`/trips/${id}/start/`)
    return response.data
}

export async function completeTrip(id: number): Promise<TripActionResponse> {
    const response = await client.post<TripActionResponse>(`/trips/${id}/complete/`)
    return response.data
}

export async function cancelTrip(id: number): Promise<TripActionResponse> {
    const response = await client.post<TripActionResponse>(`/trips/${id}/cancel/`)
    return response.data
}

export async function forceCompleteTrip(id: number, force_reason: string): Promise<TripActionResponse> {
    const response = await client.post<TripActionResponse>(`/trips/${id}/force_complete/`, { force_reason })
    return response.data
}

export async function deleteTrip(id: number): Promise<void> {
    await client.delete(`/trips/${id}/`)
}

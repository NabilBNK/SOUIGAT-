import client from './client'
import type { Trip, TripCreate, TripFilters } from '../types/trip'

interface PaginatedResponse<T> {
    count: number
    next: string | null
    previous: string | null
    results: T[]
}

export async function getTrips(filters?: TripFilters): Promise<PaginatedResponse<Trip>> {
    const response = await client.get<PaginatedResponse<Trip>>('/trips/', { params: filters })
    return response.data
}

export async function getTrip(id: number): Promise<Trip> {
    const response = await client.get<Trip>(`/trips/${id}/`)
    return response.data
}

export async function createTrip(data: TripCreate): Promise<Trip> {
    const response = await client.post<Trip>('/trips/', data)
    return response.data
}

export async function startTrip(id: number): Promise<Trip> {
    const response = await client.post<Trip>(`/trips/${id}/start/`)
    return response.data
}

export async function completeTrip(id: number): Promise<Trip> {
    const response = await client.post<Trip>(`/trips/${id}/complete/`)
    return response.data
}

export async function cancelTrip(id: number): Promise<Trip> {
    const response = await client.post<Trip>(`/trips/${id}/cancel/`)
    return response.data
}

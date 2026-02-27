import client from './client'
import type { PassengerTicket } from '../types/ticket'

export async function getTripPassengerTickets(tripId: number): Promise<PassengerTicket[]> {
    const response = await client.get<{ count: number; results: PassengerTicket[] }>(`/tickets/`, { params: { trip: tripId } })
    return response.data.results
}

export async function getPassengerTickets(params?: Record<string, unknown>): Promise<{ count: number; results: PassengerTicket[] }> {
    const response = await client.get('/tickets/', { params })
    return response.data
}

export async function createPassengerTicket(
    tripId: number,
    data: { passenger_name: string; seat_number?: string; payment_source: string }
): Promise<PassengerTicket> {
    const response = await client.post<PassengerTicket>(`/tickets/`, { ...data, trip: tripId })
    return response.data
}

export async function updatePassengerTicket(
    id: number,
    data: Partial<PassengerTicket>
): Promise<PassengerTicket> {
    const response = await client.patch<PassengerTicket>(`/tickets/${id}/`, data)
    return response.data
}

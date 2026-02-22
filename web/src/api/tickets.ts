import client from './client'
import type { PassengerTicket } from '../types/ticket'

export async function getTripPassengerTickets(tripId: number): Promise<PassengerTicket[]> {
    const response = await client.get<PassengerTicket[]>(`/trips/${tripId}/passenger-tickets/`)
    return response.data
}

export async function createPassengerTicket(
    tripId: number,
    data: { passenger_name: string; seat_number?: string; payment_source: string }
): Promise<PassengerTicket> {
    const response = await client.post<PassengerTicket>(`/trips/${tripId}/passenger-tickets/`, data)
    return response.data
}

export async function updatePassengerTicket(
    id: number,
    data: Partial<PassengerTicket>
): Promise<PassengerTicket> {
    const response = await client.patch<PassengerTicket>(`/tickets/${id}/`, data)
    return response.data
}

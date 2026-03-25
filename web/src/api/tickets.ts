import client from './client'
import type { PassengerTicket } from '../types/ticket'
import { queuePassengerTicketDelete, queuePassengerTicketUpsert } from '../sync/operationalSync'

interface PaginatedTicketsResponse {
    count: number
    next: string | null
    previous?: string | null
    results: PassengerTicket[]
}

export async function getTripPassengerTickets(tripId: number): Promise<PassengerTicket[]> {
    const pageSize = 100
    let page = 1
    const all: PassengerTicket[] = []

    // Trip detail needs complete local totals, not just the first paginated page.
    while (true) {
        const response = await client.get<PaginatedTicketsResponse>('/tickets/', {
            params: { trip: tripId, page, page_size: pageSize },
        })
        all.push(...response.data.results)
        if (!response.data.next || response.data.results.length === 0) break
        page += 1
    }

    return all
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
    void queuePassengerTicketUpsert(response.data)
    return response.data
}

export async function updatePassengerTicket(
    id: number,
    data: Partial<PassengerTicket>
): Promise<PassengerTicket> {
    const response = await client.patch<PassengerTicket>(`/tickets/${id}/`, data)
    void queuePassengerTicketUpsert(response.data)
    return response.data
}

export async function deletePassengerTicket(id: number): Promise<void> {
    await client.delete(`/tickets/${id}/`)
    void queuePassengerTicketDelete(id)
}
